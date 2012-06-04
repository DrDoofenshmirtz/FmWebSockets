(ns
  ^{:doc "Stuff for handling the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.protocol
  (:use
    [clojure.contrib.def :only (defvar-)]
    [clojure.contrib.json :only (json-str)]
    [fm.core.lazy-seqs :only (split-after split-after-tail)]
    [fm.core.bytes :only (signed-byte number<-bytes number->bytes)])
  (:import
    (java.io ByteArrayInputStream InputStreamReader BufferedReader
             OutputStreamWriter BufferedWriter)
    (org.apache.commons.codec.digest DigestUtils)
    (org.apache.commons.codec.binary Base64)))

(defvar- ws-spec-guid "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")

(defvar- request-keys-by-line-start {"Connection: "        :connection
                                     "Upgrade: "           :upgrade
                                     "Host: "              :host
                                     "Origin: "            :origin
                                     "Sec-WebSocket-Key: " :sec-ws-key})

(defvar- opcode-keys-by-opcode-values {0x0 :message-continuation
                                       0x1 :text-message
                                       0x2 :binary-message
                                       0x8 :connection-close
                                       0x9 :ping
                                       0xA :pong})

(defvar- opcode-values-by-opcode-keys {:message-continuation 0x0
                                       :text-message         0x1
                                       :binary-message       0x2
                                       :connection-close     0x8
                                       :ping                 0x9
                                       :pong                 0xA})

(defn- add-request-entry [request line]
  (if-let [request-entry (some
                           (fn [[line-start request-key]]
                             (if (.startsWith line line-start)
                               [request-key
                                (.substring line (.length line-start))]))
                           request-keys-by-line-start)]
    (apply assoc request request-entry)
    request))

(defn- parse-connect-request [unsigned-request-bytes]
  (let [request-bytes (byte-array (map signed-byte unsigned-request-bytes))
        request-lines (-> request-bytes
                          ByteArrayInputStream.
                          InputStreamReader.
                          BufferedReader.
                          line-seq)]
    (reduce add-request-entry {} request-lines)))

(defn read-connect-request [unsigned-byte-seq]
  (let [[request-bytes tail]
        (split-after-tail '(13 10 13 10) unsigned-byte-seq)]
    [(parse-connect-request request-bytes) tail]))

(defn- sec-ws-accept [sec-ws-key]
  (.encodeToString
    (Base64.)
    (DigestUtils/sha (str sec-ws-key ws-spec-guid))))

(defn write-connect-response [output-stream connect-request]
  (let [sec-ws-accept (sec-ws-accept (:sec-ws-key connect-request))
        writer (-> output-stream OutputStreamWriter. BufferedWriter.)]
    (doto writer
      (.write "HTTP/1.1 101 Switching Protocols")
      (.newLine)
      (.write "Upgrade: websocket")
      (.newLine)
      (.write "Connection: Upgrade")
      (.newLine)
      (.write "Sec-WebSocket-Accept: ")
      (.write sec-ws-accept)
      (.newLine)
      (.newLine)
      (.flush))))

(defn- read-payload-bytes [unsigned-byte-seq length]
  (let [[payload-bytes tail] (split-at length unsigned-byte-seq)]
    [(number<-bytes payload-bytes) tail]))

(defn- read-payload-length [message unsigned-byte-seq]
  (let [payload-length (:payload-length message)
        [payload-length tail] (case payload-length
                                126 (read-payload-bytes unsigned-byte-seq 2)
                                127 (read-payload-bytes unsigned-byte-seq 8)
                                [payload-length unsigned-byte-seq])]
    [(assoc message :payload-length payload-length) tail]))

(defn- read-mask-bytes [message unsigned-byte-seq]
  (let [[mask-bytes tail] (split-at 4 unsigned-byte-seq)]
    [(assoc message :mask-bytes mask-bytes) tail]))

(defn- masked-seq [numbers mask-numbers]
  (let [mask-numbers (cycle mask-numbers)
        mask (fn mask [numbers mask-numbers]
      (lazy-seq (if (seq numbers)
                  (cons
                    (bit-xor (first numbers) (first mask-numbers))
                    (mask (rest numbers) (rest mask-numbers))))))]
    (mask numbers mask-numbers)))

(defn- read-payload [message unsigned-byte-seq]
  (let [{:keys [payload-length mask-bytes]} message
        [payload-bytes tail] (split-at payload-length unsigned-byte-seq)
        payload-bytes (masked-seq payload-bytes mask-bytes)]
    [(assoc message :payload payload-bytes) tail]))

(defn- read-header [message unsigned-byte-seq]
  (let [[final?-rsvs-opcode masked?-payload-length & tail] unsigned-byte-seq
        final-fragment? (bit-test final?-rsvs-opcode 7)
        opcode (bit-and final?-rsvs-opcode 0xF)
        opcode (opcode-keys-by-opcode-values opcode :unknown)
        masked? (bit-test masked?-payload-length 7)
        payload-length (bit-and masked?-payload-length 0x7F)]
    [(assoc message
       :final-fragment? final-fragment?
       :opcode opcode
       :masked? masked?
       :payload-length payload-length)
     tail]))

(defn read-message [unsigned-byte-seq]
  (loop [input unsigned-byte-seq
         message {}
         readers [read-header
                  read-payload-length
                  read-mask-bytes
                  read-payload]]
    (if (and (seq readers) (seq input))
      (let [[message input] ((first readers) message input)]
        (recur input message (rest readers)))
      [(if-not (empty? message) message) input])))

(defn message-seq [unsigned-byte-seq]
  (lazy-seq (let [[message tail] (read-message unsigned-byte-seq)]
              (if (and message (not= :connection-close (:opcode message)))
                (cons message (message-seq tail))))))

(defn message-seq-seq [unsigned-byte-seq]
  (letfn [(chunked-message-seq [message-seq]
            (lazy-seq (if (seq message-seq)
                        (let [[head tail]
                              (split-after :final-fragment? message-seq)]
                          (cons head (chunked-message-seq tail))))))]
    (chunked-message-seq (message-seq unsigned-byte-seq))))

(defn- payload-length [length]
  (if (<= length 125)
    [length]
    (let [length-bytes (number->bytes length)
          byte-count (count length-bytes)]
      (case byte-count
        1 [126 (cons 0 length-bytes)]
        2 [126 length-bytes]
        3 [127 (cons 0 length-bytes)]
        4 [127 length-bytes]
        (throw (IllegalStateException.
                 "Payload is too long for a single fragment!"))))))

(defn- generate-mask-bytes []
  (take 4 (repeatedly #(rand-int 256))))

(defn send-bytes [output-stream bytes opcode-key final-fragment?]
  (let [opcode-value (opcode-values-by-opcode-keys opcode-key)
        final?-rsvs-opcode (if final-fragment?
                             (bit-set opcode-value 7)
                             opcode-value)
        [payload-length payload-length-bytes] (payload-length (count bytes))
        mask-bytes (generate-mask-bytes)
        message-bytes (concat
                        [final?-rsvs-opcode (bit-set payload-length 7)]
                        payload-length-bytes
                        mask-bytes
                        (masked-seq bytes mask-bytes))
        message-bytes (byte-array (map signed-byte message-bytes))]
    (doto output-stream
      (.write message-bytes 0 (count message-bytes))
      (.flush))))

(defn send-text [output-stream text]
  (send-bytes output-stream (.getBytes text) :text-message true))

(defn send-object [output-stream object]
  (send-text output-stream (json-str object)))
