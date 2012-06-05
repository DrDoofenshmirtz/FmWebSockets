(ns fm.websockets.core
  (:use
    [fm.websockets.server :only (start-up)]
    [fm.websockets.protocol :only (send-object)]
    [fm.websockets.connection :only (with-output)]))

(defn- send-greeting-and-print-messages [connection]
  (with-output connection
    (send-object %out {:id 1 :result "Connected!"}))
  (loop [messages (:messages connection)]
    (if-let [message (first messages)]
      (do
        (println message)
        (recur (rest messages)))
      (println "Connection closed. Bye!"))))

(start-up 17500 send-greeting-and-print-messages)
