(ns
  ^{:doc "Support for JSON as an RPC format."
    :author "Frank Mosebach"}
  fm.websockets.rpc.json
  (:require
    [clojure.contrib.json :as json]
    [fm.websockets.protocol :as prot]
    [fm.websockets.rpc.core :as rpc]
    [fm.websockets.rpc.format :as fmt]))

(fmt/declare-format json)

(defn- object->content [object]
  (let [object (if (nil? object) true object)]
    (json/json-str object)))

(defn connection-handler []
  (rpc/connection-handler rpc-format))

(defmethod fmt/message->request rpc-format [_ message]
  (when (and message (prot/text-message? message))
    (json/read-json (prot/message-content message))))

