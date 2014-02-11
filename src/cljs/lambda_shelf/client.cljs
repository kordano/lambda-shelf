(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))

;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))


(def app-state (atom {:bookmarks
                      [{:title "the master himself" :url "https://github.com/kordano" :date (new js/Date)}
                       {:title "nomads blog" :url "http://functional-nomads.github.io" :date (new js/Date)}
                       {:title "solar raspberry" :url "http://www.instructables.com/id/Solar-Powered-Raspberry-Pi/?ALLSTEPS" :date (new js/Date)}]}))


(defn handle-url-change [e owner {:keys [url-text]}]
  (om/set-state! owner :url-text (.. e -target -value)))

(defn handle-title-change [e owner {:keys [title-text]}]
  (om/set-state! owner :title-text (.. e -target -value)))

(defn add-bookmark [app owner]
  (let [new-title (.-value (om/get-node owner "new-title"))
        new-url (.-value (om/get-node owner "new-url"))]
    (om/transact! app :bookmarks conj {:title new-title :url new-url :date (new js/Date)})
    (om/set-state! owner :url-text "")
    (om/set-state! owner :title-text "")))


(defn bookmark-view [{:keys [title url date] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [delete]}]
      (dom/li nil
              (dom/span nil (dom/a #js {:href url} title))
              (dom/button #js {:onClick (fn [e] (put! delete @bookmark)) :className "remove-button"} "X")))))


(defn bookmarks-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:delete (chan)
       :url-text ""
       :title-text ""})
    om/IWillMount
    (will-mount [_]
      (let [delete (om/get-state owner :delete)]
        (go (loop []
              (let [bookmark (<! delete)]
                (om/transact! app :bookmarks
                              (fn [xs] (into [] (remove #(= bookmark %) xs))))
                (recur))))))
    om/IRenderState
    (render-state [this state]
      (dom/div nil
               (dom/h2 nil "Bookmarks")
               (apply dom/ul nil
                      (om/build-all bookmark-view (:bookmarks app) {:init-state {:delete (:delete state)}}))
               (dom/div #js {:className "input-div"}
                        (dom/span nil "URL:"
                                  (dom/input #js {:type "text" :ref "new-url" :value (:url-text state) :onChange #(handle-url-change % owner state)}))
                        (dom/span nil "Title:"
                                  (dom/input #js {:type "text" :ref "new-title" :value (:title-text state) :onChange #(handle-title-change % owner state)}))

                        (dom/button #js {:onClick #(add-bookmark app owner) :className "add-button"} "ADD"))))))


(om/root
  app-state
  bookmarks-view
  (. js/document (getElementById "bookmarks")))
