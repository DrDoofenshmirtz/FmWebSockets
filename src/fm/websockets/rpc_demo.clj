(ns
  ^{:doc "A demo namespace to be used with a namespace RPC dispatcher."
    :author "Frank Mosebach"}
  fm.websockets.rpc-demo)

(defn ws-success [connection & args]
  (println (format "ws-success %s" args)))

(defn ws-error [connection & args]
  (println (format "ws-error %s" args))
  (throw (Exception. "RPC failed!")))
