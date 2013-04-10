(ns
  ^{:doc "RPC types."    
    :author "Frank Mosebach"}
  fm.websockets.rpc.types)

(defprotocol Result
  "Defines the return value type of a request handler."
  (connection [this] 
    "Returns the state of the connection after a request has been processed.")
  (value [this] 
    "Returns the actual result that is to be sent to the client.")
  (error? [this] 
    "Returns true if processing a request failed, otherwise false."))

