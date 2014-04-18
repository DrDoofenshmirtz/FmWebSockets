(ns
  ^{:doc 
  
  "Create a connection handler for a WebSockets server that hosts JSON RPC 
  services."
  
    :author "Frank Mosebach"}
  fm.websockets.app.handler
  (:require    
    [fm.websockets.resources :as rscs]
    [fm.websockets.ping-pong :as ppg]
    [fm.websockets.rpc.core :as rpc]
    [fm.websockets.rpc.targets :as tar]
    [fm.websockets.rpc.json :as jrpc]
    [fm.websockets.message-loop :as mloop]
    [fm.websockets.app.config :as cfg]))

(defn- request-handler [service-namespaces]
  (rscs/request-handler (apply tar/target-router service-namespaces)))

(defn- message-handler [request-handler]
  (-> request-handler
      rpc/message-handler
      ppg/message-handler
      rscs/message-handler))

(defn- connection-handler [message-handler config resource-store-constructor]
  (-> (comp (mloop/connection-handler message-handler) 
            (jrpc/connection-handler)
            (ppg/connection-handler)
            (cfg/connection-handler config))
      (rscs/connection-handler resource-store-constructor)))

(defn app-handler 
  "Creates a connection handler that processes incoming JSON RPC requests,
  forwarding them to the appropriate rpc targets."
  [config resource-store-constructor]
  (-> config
      :services
      request-handler
      message-handler
      (connection-handler config resource-store-constructor)))

