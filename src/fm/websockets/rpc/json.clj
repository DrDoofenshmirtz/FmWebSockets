(ns
  ^{:doc "Support for JSON as an rpc format."
    :author "Frank Mosebach"}
  fm.websockets.rpc.json
  (:require
    [clojure.contrib.json :as json]
    [fm.websockets.protocol :as prot]
    [fm.websockets.rpc.core :as rpc]))

(defn message->request [message]
  (when (and message (prot/text-message? message))
    (json/read-json (prot/message-content message))))

(defn object->content [object]
  (let [object (if (nil? object) true object)]
    (json/json-str object)))

(defn connection-handler []
  (rpc/connection-handler message->request object->content))

