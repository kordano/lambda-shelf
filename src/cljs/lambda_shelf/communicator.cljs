(ns lambda-shelf.communicator
  (:require [goog.net.XhrIo :as xhr]
            [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! chan close! put!]])
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]]))


(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (put! ch (-> event .-target .getResponseText))
                (close! ch)))
    ch))


(defn POST [url payload]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (put! ch (-> event .-target .getResponseText))
                (close! ch))
              "POST"
              payload)
    ch))


(defn get-edn [url]
  (go
   (-> (GET url) <! read-string)))


(defn post-edn [url payload]
  (go
   (-> (POST url (.toString payload))
       <!
       read-string)))


;; --- WEBSOCKET CONNECTION ---
;; based on https://github.com/loganlinn/cljs-websockets-async

(defn connect!
  ([uri] (connect! uri {}))
  ([uri {:keys [in out] :or {in chan out chan}}]
      (let [on-connect (chan)
            in (in)
            out (out)
            websocket (js/WebSocket. uri)]
        (.log js/console "establishing websocket ...")
        (doto websocket
          (aset "onopen" (fn []
                           (close! on-connect)
                           (.log js/console "channel opened")
                           (go-loop []
                                    (let [data (<! in)]
                                      (if-not (nil? data)
                                        (do (.send websocket (pr-str data))
                                            (recur))
                                        (do (close! out)
                                            (.close websocket)))))))
          (aset "onclose" (fn []
                            (.log js/console "channel closed")
                            (close! in)
                            (close! out)))
          (aset "onerror" (fn [e] (.log js/console (str "ERROR:" e))))
          (aset "onmessage" (fn [m]
                              (let [data (read-string (.-data m))]
                                (.log js/console)
                                (put! out data)))))
        (go
          (<! on-connect)
          {:uri uri :websocket websocket :in in :out out}))))

#_(go
  (let [connection (<! (connect! "ws://localhost:8080/bookmark/ws"))]
    (>! (:in connection) {:topic :greeting :data ""})
    (.log js/console (str (<! (:out connection))))
    (>! (:in connection) {:topic :get-all :data ""})
    (.log js/console (str (count (<! (:out connection)))))
    (.close (:websocket connection))))
