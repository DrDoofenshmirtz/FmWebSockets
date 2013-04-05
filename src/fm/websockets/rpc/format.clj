(ns
  ^{:doc "RPC formats for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.rpc.format)

(defmacro declare-format
  ([id]
   `(declare-format ~id ~(symbol (str *ns*))))
  ([id ns]
   `(def ~(symbol (str *ns*) "rpc-format") 
          {:id ~(keyword (str *ns*) (name id)) :ns '~ns})))

(defn use-format [{ns :ns :as rpc-format}]
  (if (nil? ns)
    (throw (IllegalArgumentException. 
             (format "Illegal rpc format %s: namespace is missing!" 
                     rpc-format))))
  (if-not (find-ns ns)
    (require ns))
  rpc-format)

(defn- format-id [{id :id} & args]
  id)

(defmulti message->request rpc-format)

(defmulti result->content rpc-format)

(defmulti error->content rpc-format)

(defmulti request->content rpc-format)

(defmulti notification->content rpc-format)

