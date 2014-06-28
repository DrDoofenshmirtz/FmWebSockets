(ns
  ^{:doc 
  
  "Support for the convenient definition of targets for remote procedure calls."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.targets
  (:refer-clojure :exclude [read])
  (:require
    [clojure.contrib.str-utils :as stu]
    [fm.core.hyphenate :as hy]
    [fm.resources.core :as rsc]
    [fm.websockets.resources :as wsr]
    [fm.websockets.rpc.request :as req])
  (:import
    (java.util UUID)
    (java.util.regex Pattern)))

(defmacro defroute [route]
  (let [route-name  (gensym "__route__")
        target-meta {::target {::name `'~route-name ::type ::route}}]    
   `(def ~(vary-meta route-name merge target-meta)
          (vary-meta ~route merge ~target-meta))))

(defn request-name-route [] 
  (fn [connection request]
    (-> request :name str hy/hyphenate symbol)))

(defn prefixed-request-name-route [expected-prefix segment-separator]
  (let [expected-prefix  (str expected-prefix)
        quoted-separator (Pattern/quote segment-separator)]
    (fn [connection request]
      (let [prefixed-name   (-> request :name str)
            name-segments   (.split prefixed-name quoted-separator)
            prefix-segments (butlast name-segments)
            prefix          (stu/str-join segment-separator 
                                          (map hy/hyphenate prefix-segments))]
        (when (= expected-prefix prefix)
          (symbol (hy/hyphenate (last name-segments))))))))

(defn- gen-action [name body]
  (if (= '=> (first body))
    (second body)
    `(fn ~name ~@body)))

(defmacro defaction [name & more]
  (let [[[doc-string? attributes?] more] (split-with #(not (or (= '=> %) 
                                                               (sequential? %))) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::action}}]    
   `(def ~(vary-meta name merge var-meta target-meta)
          (vary-meta ~(gen-action name more) merge ~target-meta))))

(defn- wrap-resource [resource close!]
  {::resource resource
   ::close!   close!})

(defn- update-resource [wrapped-resource update]
  (-> wrapped-resource update rsc/good))

(defn- close-resource! [{resource ::resource close! ::close!}]
  (close! resource))

(def ^{:private true} resource-slots {::update update-resource})

(defn- channel-id []
  (str (UUID/randomUUID)))

(defn- channel-id? [id]
  (-> id str .trim .length (> 0)))

(defn- check-channel-id [id]
  (if-not (channel-id? id)
    (throw (IllegalArgumentException. (format "Illegal channel id: '%s'!" id))))
  id)

(def ^{:private true :const true} channel-context :connection)

(defn- get-resource [connection channel-id]
  (if-let [resource (-> connection 
                        (wsr/get-resource channel-context 
                                          (check-channel-id channel-id)) 
                        ::resource)]
    resource
    (throw (IllegalStateException. "Channel closed or aborted!"))))

(defn- open [connection open slots args]
  (let [resource (apply open args)
        close!   (:abort slots)
        resource (wrap-resource resource close!)
        id       (channel-id)]
    (wsr/store! connection channel-context 
                id resource :connection
                :close! close-resource!
                :slots  resource-slots)
    id))

(def ^{:private true :dynamic true} *resource* nil)

(defn- ensure-resource []
  (if (nil? *resource*)
    (throw (IllegalStateException. "No resource in current context!"))
    true))

(defn alter-resource! [func & args]
  (ensure-resource)
  (set! *resource* (apply func *resource* args)))

(defn- read [connection read slots [id & args]]
  (binding [*resource* (get-resource connection id)]
    (let [result (apply read *resource* args)]
      (wsr/send-to! connection [id] ::update #(assoc % ::resource *resource*))
      (get-resource connection id)
      result)))

(defn- write [connection write slots [id & args]]
  (binding [*resource* (get-resource connection id)]
    (let [result (apply write *resource* args)]
      (wsr/send-to! connection [id] ::update #(assoc % ::resource *resource*))
      (get-resource connection id)
      result)))

(defn- abort [connection abort slots [id]]
  (let [id (check-channel-id id)]
    (wsr/remove! connection channel-context id)
    nil))

(defn- close [connection close slots [id]]
  (let [id        (check-channel-id id)
        resources (wsr/send-to! connection 
                                [id] 
                                ::update 
                                #(assoc % ::close! close))
        close!    (get-in resources [:contents id :resource ::close!])]
    (when-not (identical? close close!)
      (throw (IllegalStateException. "Channel closed or aborted!")))
    (wsr/remove! connection channel-context id)
    nil))

(defn- operation->keyword [operation]
  (-> operation str .trim .toLowerCase keyword))

(defn channel [slots]
  (fn [operation & args]
    (let [operation (operation->keyword operation)
          slot      (slots operation)]
      (when-not slot
        (throw (UnsupportedOperationException. 
                 (format "Channel operation not supported: '%s'!" operation))))
      ((case operation
         :open  open
         :read  read
         :write write
         :abort abort
         :close close) (req/connection) slot slots args))))

(def ^{:private true} operation-keys #{:open :read :write :abort :close})

(defmacro defchannel [name & more]
  (let [[[doc-string? attributes?] more] (split-with (complement operation-keys) 
                                                     more)
        var-meta    (if (string? doc-string?)
                      (assoc attributes? :doc doc-string?)
                      attributes?)
        target-meta {::target {::name `'~name ::type ::channel}}]
   `(def ~(vary-meta name merge var-meta target-meta)
          (vary-meta (channel (hash-map ~@more)) merge ~target-meta))))

(defn- add-target [targets [target-attributes target]]
  (assoc-in targets 
            [(::type target-attributes) (::name target-attributes)] 
            @target))

(defn- target-attributes [target]
  (::target (meta target)))

(defn ns-targets [ns]
  (reduce add-target 
          {}           
          (map (juxt target-attributes identity) 
               (filter target-attributes 
                       (vals (ns-interns ns))))))

(defn find-target [targets connection request]
  (when-let [route (-> targets ::route first second)]
    (some identity (map (fn [target-type] 
                          (when-let [name (route connection request)]
                            (get-in targets [target-type name]))) 
                        [::action ::channel]))))

(defn target-router 
  ([]
    (target-router (ns-name *ns*)))
  ([ns-name & ns-names]
    (let [ns-names (cons ns-name ns-names)
          targets  (map (fn [ns-name] 
                          (require ns-name) 
                          (ns-targets ns-name)) 
                        ns-names)]
      (req/request-router (fn [connection request]
                            (some #(find-target % connection request) 
                                  targets))))))

