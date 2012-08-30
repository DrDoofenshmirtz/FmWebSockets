(ns
  ^{:doc "Stuff for handling the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.protocol
  (:use
    [clojure.contrib.def :only (defvar-)]
    [fm.core.lazy-seqs :only (split-after split-after-tail)]
    [fm.core.bytes :only (signed-byte number<-bytes number->bytes)])
  (:import
    (java.io ByteArrayInputStream InputStreamReader BufferedReader
             OutputStreamWriter BufferedWriter)
    (org.apache.commons.codec.digest DigestUtils)
    (org.apache.commons.codec.binary Base64)))

(defvar- request-header-pattern #"^\s*([^\:]+)\:\s*(.*)$")

(defvar- ws-spec-guid "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")

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

(defn- parse-request-headers [request-lines]
  (reduce
    conj
    {}
    (map
      (fn [[_ key value]]
        [(keyword (.toLowerCase key)) value])
      (filter
        identity
        (map
          #(re-find request-header-pattern %)
          request-lines)))))

(defn- parse-connect-request [unsigned-request-bytes]
  (let [request-bytes (byte-array (map signed-byte unsigned-request-bytes))
        request-lines (-> request-bytes
                          ByteArrayInputStream.
                          InputStreamReader.
                          BufferedReader.
                          line-seq)]
        request-lines (filter #(> (.length (.trim %)) 0) request-lines)
    {:request-line (first request-lines)
     :request-headers (parse-request-headers (rest request-lines))}))

(defn read-connect-request [unsigned-byte-seq]
  (let [[request-bytes tail]
        (split-after-tail '(13 10 13 10) unsigned-byte-seq)]
    [(parse-connect-request request-bytes) tail]))

(defn- sec-ws-accept [sec-ws-key]
  (.encodeToString
    (Base64.)
    (DigestUtils/sha (str sec-ws-key ws-spec-guid))))

(defn write-connect-response [output-stream connect-request]
  (let [sec-ws-accept (sec-ws-accept (-> connect-request
                                         :request-headers
                                         :sec-websocket-key))
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

(defn- read-payload-length [fragment unsigned-byte-seq]
  (let [payload-length (:payload-length fragment)
        [payload-length tail] (case payload-length
                                126 (read-payload-bytes unsigned-byte-seq 2)
                                127 (read-payload-bytes unsigned-byte-seq 8)
                                [payload-length unsigned-byte-seq])]
    [(assoc fragment :payload-length payload-length) tail]))

(defn- read-mask-bytes [fragment unsigned-byte-seq]
  (let [[mask-bytes tail] (split-at 4 unsigned-byte-seq)]
    [(assoc fragment :mask-bytes mask-bytes) tail]))

(defn- masked-seq [numbers mask-numbers]
  (let [mask-numbers (cycle mask-numbers)
        mask (fn mask [numbers mask-numbers]
               (lazy-seq (if (seq numbers)
                           (cons
                             (bit-xor (first numbers) (first mask-numbers))
                             (mask (rest numbers) (rest mask-numbers))))))]
    (mask numbers mask-numbers)))

(defn- read-payload [fragment unsigned-byte-seq]
  (let [{:keys [payload-length mask-bytes]} fragment
        [payload-bytes tail] (split-at payload-length unsigned-byte-seq)
        payload-bytes (masked-seq payload-bytes mask-bytes)]
    [(assoc fragment :payload payload-bytes) tail]))

(defn- read-header [fragment unsigned-byte-seq]
  (let [[final?-rsvs-opcode masked?-payload-length & tail] unsigned-byte-seq
        final-fragment? (bit-test final?-rsvs-opcode 7)
        opcode (bit-and final?-rsvs-opcode 0xF)
        opcode (opcode-keys-by-opcode-values opcode :unknown)
        masked? (bit-test masked?-payload-length 7)
        payload-length (bit-and masked?-payload-length 0x7F)]
    [(assoc fragment
       :final-fragment? final-fragment?
       :opcode opcode
       :masked? masked?
       :payload-length payload-length)
     tail]))

(defn- read-fragment [unsigned-byte-seq]
  (loop [input unsigned-byte-seq
         fragment {}
         readers [read-header
                  read-payload-length
                  read-mask-bytes
                  read-payload]]
    (if (and (seq readers) (seq input))
      (let [[fragment input] ((first readers) fragment input)]
        (recur input fragment (rest readers)))
      [(if-not (empty? fragment) fragment) input])))

(defn- fragment-seq [unsigned-byte-seq]
  (lazy-seq (let [[fragment tail] (read-fragment unsigned-byte-seq)]
              (if (and fragment (not= :connection-close (:opcode fragment)))
                (cons fragment (fragment-seq tail))))))

(defn message-seq [unsigned-byte-seq]
  (letfn [(chunked-fragment-seq [fragment-seq]
            (lazy-seq (if (seq fragment-seq)
                        (let [[head tail]
                              (split-after :final-fragment? fragment-seq)]
                          (cons head (chunked-fragment-seq tail))))))]
    (chunked-fragment-seq (fragment-seq unsigned-byte-seq))))

(defn opcode [message]
  (:opcode (first message)))

(defn binary-message? [message]
  (= :binary-message (opcode message)))

(defn text-message? [message]
  (= :text-message (opcode message)))

(defn payload-bytes [message]
  (if (seq message)
    (lazy-cat
      (:payload (first message))
      (payload-bytes (rest message)))))

(defmulti message-content opcode)

(defmethod message-content :binary-message [message]
  (payload-bytes message))

(defmethod message-content :text-message [message]
  (-> (map signed-byte (payload-bytes message)) byte-array String.))

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
        fragment-bytes (concat
                         [final?-rsvs-opcode (bit-set payload-length 7)]
                         payload-length-bytes
                         mask-bytes
                         (masked-seq bytes mask-bytes))
        fragment-bytes (byte-array (map signed-byte fragment-bytes))]
    (doto output-stream
      (.write fragment-bytes 0 (count fragment-bytes))
      (.flush))))

(defn send-binary-message [output-stream bytes]
  (send-bytes output-stream bytes :binary-message true))

(defn send-text-message [output-stream text]
  (send-bytes output-stream (.getBytes text) :text-message true))
