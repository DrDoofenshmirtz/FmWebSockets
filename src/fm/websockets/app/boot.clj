(ns
  ^{:doc 
  
  "Define a Hook supposed to be called when a WebSockets app is booted."
  
    :author "Frank Mosebach"}
  fm.websockets.app.boot
  (:require
    [clojure.contrib.logging :as log]))

(defmacro def-boot-hook [hook]
  (let [hook-name (gensym "__boot-hook__")
        hook-meta {::hook {::name `'~hook-name ::type ::boot}}]    
   `(def ~(vary-meta hook-name merge hook-meta)
          (vary-meta ~hook merge ~hook-meta))))

(defn- hook-attributes [hook]
  (::hook (meta hook)))

(defn- boot-hook [hook]
  (when (= ::boot (-> hook hook-attributes ::type))
    hook))

(defn find-boot-hook [ns-name]
  (require ns-name)
  (->> (ns-interns ns-name)
       vals
       (some boot-hook)))

