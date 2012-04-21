(ns fm.websockets.core
  (:use
    [fm.core.lazy-seqs :only (unsigned-byte-seq)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.protocol :only (read-connect-request
                                   write-connect-response
                                   send-object
                                   message-seq-seq)]))

(defn- dump-bytes [byte-seq]
  (loop [byte-seq byte-seq next-byte (first byte-seq)]
    (when next-byte
      (println next-byte "=" (Integer/toBinaryString next-byte))
      (recur (rest byte-seq) (first (rest byte-seq))))))

(defn- read-http-message [socket]
  (let [input-stream (.getInputStream socket)
        output-stream (.getOutputStream socket)
        byte-seq (unsigned-byte-seq input-stream)
        [connect-request byte-seq] (read-connect-request byte-seq)]
    (println connect-request)
    (write-connect-response output-stream connect-request)
    (send-object output-stream {:ticket 42 :result "Guten Tag, Mausi!" :ok true})
    (loop [message-seq-seq (message-seq-seq byte-seq)]
      (if-let [message-seq (first message-seq-seq)]
        (do
          (println (map first message-seq))
          (recur (rest message-seq-seq)))
        (println "Connection closed. Bye!")))))

(defn- handle-connection [socket]
  (try
    (read-http-message socket)
    (catch Throwable t
      (println "!-Error:" t)
      (.printStackTrace t)
      (throw t))))

(start-up 17500 handle-connection #(println %))
