(ns
  ^{:doc 
  
  "Support for the convenient definition of targets for remote procedure calls."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.targets
  (:require
    [fm.core.hyphenate :as hy]
    [fm.websockets.rpc.request :as req]))

(defmacro defaction [name & more]
  (let [[[doc-string? attributes?] more] (split-with (complement sequential?) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::action}}]    
   `(def ~(vary-meta (symbol (str name)) merge var-meta target-meta)
          (vary-meta (fn ~name ~@more) merge ~target-meta))))

(defn- operation->keyword [operation]
  (-> operation str .trim .toLowerCase keyword))

(defn- open [connection open slots args])

(defn- receive [connection receive slots args])

(defn- abort [connection abort slots args])

(defn- close [connection close slots args])

(defn channel [slots]
  (fn [operation & args]
    (let [operation (operation->keyword operation)
          slot      (slots operation)]
      (when-not slot
        (throw (IllegalArgumentException. 
                 (format "Illegal channel operation: '%s'!" operation))))
      ((case operation
         :open    open
         :receive receive
         :abort   abort
         :close   close) (req/connection) operation slots args))))

(defmacro defchannel [name & more])

(defn- target-name [request]
  (-> request :name symbol hy/hyphenate))

(defn- target-finder [request]
  (let [target-name (target-name request)]
    (fn [lookup]
      (let [target (lookup target-name)]
        (if (::target (meta target))
          target)))))

(defn target-router 
  ([]
    (target-router (ns-name *ns*)))
  ([ns-name & ns-names]
    (let [ns-names   (cons ns-name ns-names)
          ns-lookups (map (fn [ns-name] 
                            (require ns-name) 
                            (ns-interns ns-name)) 
                          ns-names)]
      (req/request-router (fn [connection request]
                            (some (target-finder request) ns-lookups))))))

