(ns
  ^{:doc "Stuff for handling the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.protocol
  (:require
    [fm.core.io :as io])
  (:use
    [clojure.contrib.def :only (defvar-)]
    [fm.core.lazy-seqs :only (split-after)]
    [fm.core.bytes :only (signed-byte number<-bytes number->bytes)])
  (:import
    (java.util UUID)
    (java.io ByteArrayInputStream InputStreamReader BufferedReader
             ByteArrayOutputStream OutputStreamWriter BufferedWriter)
    (org.apache.commons.codec.digest DigestUtils)
    (org.apache.commons.codec.binary Base64)
    (fm.websockets.exceptions EndOfData)))

(defvar- end-of-data-tag (Object.))

(defvar- end-of-connect-request '(13 10 13 10))

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

(defn- parse-connect-request [^bytes request-bytes]
  (let [request-lines (-> request-bytes
                          ByteArrayInputStream.
                          InputStreamReader.
                          BufferedReader.
                          line-seq)
        request-lines (filter #(> (.length (.trim %)) 0) request-lines)]
    (if (seq request-lines)
      {:request-line (first request-lines)
       :request-headers (parse-request-headers (rest request-lines))})))

(defn read-connect-request [input-stream]
  (-> input-stream
      (io/read-until-detected end-of-connect-request)
      parse-connect-request))

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

(defn- read-byte-array [input-stream length]
  (let [^bytes byte-array (io/read-byte-array input-stream length)]
    (if (= (alength byte-array) length)
      byte-array)))

(defn- read-payload-bytes [input-stream length]
  (if-let [payload-bytes (read-byte-array input-stream length)]
    (number<-bytes payload-bytes)))

(defn- read-payload-length [fragment input-stream]
  (let [payload-length (:payload-length fragment)
        payload-length (case payload-length
                         126 (read-payload-bytes input-stream 2)
                         127 (read-payload-bytes input-stream 8)
                         payload-length)]
    (assoc fragment :payload-length payload-length)))

(defn- read-mask-bytes [fragment input-stream]
  (if-let [mask-bytes (read-byte-array input-stream 4)]
    (assoc fragment :mask-bytes mask-bytes)))

(defn- masked-byte-array [^bytes byte-array ^bytes mask-bytes]
  (let [array-length (alength byte-array) 
        mask-length  (alength mask-bytes)]
    (loop [index 0]
      (when (< index array-length)
        (aset-byte byte-array
                   index
                   (bit-xor (int (aget byte-array index)) 
                            (int (aget mask-bytes (mod index mask-length)))))
        (recur (inc index))))
    byte-array))

(defn- read-payload [fragment input-stream]
  (let [{:keys [payload-length mask-bytes]} fragment]
    (if-let [payload-bytes (read-byte-array input-stream payload-length)]
      (assoc fragment :payload (masked-byte-array payload-bytes mask-bytes)))))

(defn- read-header [fragment input-stream]
  (if-let [^bytes header-bytes (read-byte-array input-stream 2)]
    (let [final?-rsvs-opcode     (aget header-bytes 0)
          masked?-payload-length (aget header-bytes 1)
          final-fragment?        (bit-test final?-rsvs-opcode 7)
          opcode                 (bit-and final?-rsvs-opcode 0xF)
          opcode                 (opcode-keys-by-opcode-values opcode :unknown)
          masked?                (bit-test masked?-payload-length 7)
          payload-length         (bit-and masked?-payload-length 0x7F)]
      (assoc fragment
             :final-fragment? final-fragment?
             :opcode          opcode
             :masked?         masked?
             :payload-length  payload-length))))

(defn- read-fragment [input-stream]
  (letfn [(read-sections [fragment readers]
            (if (seq readers)
              (if-let [fragment ((first readers) fragment input-stream)]
                (recur fragment (rest readers)))
              (if-not (empty? fragment)
                fragment)))]
    (read-sections {} [read-header
                       read-payload-length
                       read-mask-bytes
                       read-payload])))

(defn- fragment-seq [input-stream]
  (lazy-seq (let [fragment (read-fragment input-stream)]
              (if (and fragment (not= :connection-close (:opcode fragment)))
                (cons fragment (fragment-seq input-stream))
                (cons end-of-data-tag nil)))))

(defn- final-fragment? [fragment-or-eod-tag]
  (if (identical? end-of-data-tag fragment-or-eod-tag)
    (throw (EndOfData. "End of data detected while reading messages!"))
    (:final-fragment? fragment-or-eod-tag)))

(defn message-seq [input-stream]
  (letfn [(chunked-fragment-seq [fragment-seq]
            (lazy-seq (if (seq fragment-seq)
                        (let [[head tail]
                              (split-after final-fragment? fragment-seq)]
                          (cons head (chunked-fragment-seq tail))))))]
    (chunked-fragment-seq (fragment-seq input-stream))))

(defn opcode [message]
  (:opcode (first message)))

(defn binary-message? [message]
  (= :binary-message (opcode message)))

(defn text-message? [message]
  (= :text-message (opcode message)))

(defn pong? [message]
  (= :pong (opcode message)))

(defn message-payload ^bytes [message]
  (let [^ByteArrayOutputStream buffer (ByteArrayOutputStream.)]
    (reduce (fn [^ByteArrayOutputStream buffer ^bytes byte-array]
              (.write buffer byte-array))
            buffer
            (map :payload message))
    (.toByteArray buffer)))

(defmulti message-content opcode)

(defmethod message-content :text-message [message]
  (String. (message-payload message) "UTF-8"))

(defmethod message-content :binary-message [message]
  (message-payload message))

(defmethod message-content :pong [message]
  (seq (message-payload message)))

(defmethod message-content :default [message]
  (message-payload message))

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
  (take 4 (repeatedly #(signed-byte (rand-int 256)))))

(defn- header-bytes [opcode-key final-fragment? payload-size mask-bytes]
  (let [opcode-value       (opcode-values-by-opcode-keys opcode-key)
        final?-rsvs-opcode (if final-fragment?
                             (bit-set opcode-value 7)
                             opcode-value)
        [payload-length payload-length-bytes] (payload-length payload-size)]
    (map signed-byte (concat [final?-rsvs-opcode (bit-set payload-length 7)]
                             payload-length-bytes
                             mask-bytes))))

(defn- payload-bytes [payload-bytes mask-bytes]
  (map #(signed-byte (bit-xor %1 %2)) payload-bytes (cycle mask-bytes)))

(defn send-bytes [output-stream bytes opcode-key final-fragment?]
  (let [mask-bytes     (generate-mask-bytes)
        payload-size   (count bytes)
        header-bytes   (header-bytes opcode-key
                                     final-fragment?
                                     payload-size
                                     mask-bytes)
        payload-bytes  (payload-bytes bytes mask-bytes)
        fragment-bytes (byte-array (concat header-bytes payload-bytes))]
    (doto output-stream
      (.write fragment-bytes)
      (.flush))))

(defn send-binary-message [output-stream bytes]
  (send-bytes output-stream bytes :binary-message true))

(defn send-text-message [output-stream text]
  (send-bytes output-stream (.getBytes text) :text-message true))

(defn send-ping [output-stream]
  (let [ping-bytes (.getBytes (str (UUID/randomUUID)))]
    (send-bytes output-stream ping-bytes :ping true)
    (seq ping-bytes)))
