(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))


(.log js/console "HAIL TO THE LAMBDA!")

;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))


(def app-state (atom {:bookmarks nil
                      :notifications [""]}))


(defn handle-text-change [e owner {:keys [input-text]} type]
  (om/set-state! owner [:input-text type] (.. e -target -value)))


(defn apply-to-entry [x k f]
  "Apply function f to key k in entry x"
  (fn [xs]
    (vec
     (map #(if (= x %) (assoc % k (f (k %))) %) xs))))


(defn add-bookmark [app owner]
  "read input data, send it to server and update dom"
  (let [new-url (.-value (om/get-node owner "new-url"))
        new-title (.-value (om/get-node owner "new-title"))
        new-comment (.-value (om/get-node owner "new-comment"))
        add-btn (om/get-node owner "add-btn")
        package (str {:url new-url :title new-title :comment new-comment})]
    (go
      (set! (.-innerHTML add-btn) "Adding...")
      (set! (.-disabled add-btn) true)
      (>! (om/get-state owner :incoming) (<! (post-edn "bookmark/add" package)))
      (set! (.-innerHTML add-btn) "Add!")
      (set! (.-disabled add-btn) false)
      (om/set-state! owner [:input-text :url] "")
      (om/set-state! owner [:input-text :comment] "")
      (om/set-state! owner [:input-text :title] ""))))


(defn add-bookmark-comment [{:keys [id] :as bookmark} owner]
  (let [new-comment (.-value (om/get-node owner (str "new-comment-" id)))]
    (if (= 0 (.-length (.trim new-comment)))
      (.log js/console "blank comment")
      (go
        (>! (om/get-state owner :incoming)
          (<! (post-edn
               "bookmark/comment"
               (str {:id id
                     :comment new-comment}))))
        (om/set-state! owner [:input-text :modal-comment] "")))))

(defn fetch-url-title [app owner url]
  (let [package (str {:url url})
        fetch-btn (om/get-node owner "fetch-btn")]
    (go
      (set! (.-innerHTML fetch-btn) "Fetching...")
      (set! (.-disabled fetch-btn) true)
      (let [title (:title (<! (post-edn "bookmark/fetch-title" package)))]
        (set! (.-innerHTML fetch-btn) "Fetch!")
        (set! (.-disabled fetch-btn) false)
        (om/set-state! owner [:input-text :title] title)))))


;; --- bookmark views ---

(defn comments-view [{:keys [title votes comments id] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [incoming input-text] :as state}]
      (html
       [:div.modal.fade
        {:tab-index -1
         :id (str "comments-modal-" id)
         :role "dialog"
         :aria-labelledby (str "comments-modal-label-" id)
         :aria-hidden true}
        [:div.modal-dialog
         [:div.modal-content
          [:div.modal-header
           [:button.close
            {:type "button"
             :data-dismiss "modal"
             :aria-hidden true}
            "\u00D7"]
           [:h4.modal-title {:id (str "comments-modal-label-" id)} title]]

          [:div.modal-body
           [:ul.list-group
            (map #(vec [:li.list-group-item %]) comments)]
           [:br]
           [:div.form-group
            [:textarea.form-control
             {:type "text"
              :ref (str "new-comment-" id)
              :rows 3
              :value (:modal-comment input-text)
              :style {:resize "vertical"}
              :on-change #(handle-text-change % owner state :modal-comment)
              :placeholder "What do you think?"}]]
           [:button.btn.btn-primary.btn-xs
            {:type "button"
             :on-click (fn [_] (add-bookmark-comment @bookmark owner))}
            "add comment"]]

          [:div.modal-footer
           [:button.btn.btn-default
            {:type "button"
             :data-dismiss "modal"}
            "Close"]]]]]))))


(defn bookmark-view [{:keys [title url date id votes comments] :as bookmark} owner]
  (let [comment-count (count comments)]
      (reify
        om/IRenderState
        (render-state [this {:keys [incoming input-text] :as state}]
          (html
           [:tr

            [:td.bookmark-title
             [:a {:href url :target "_blank"} title]
             [:div.panel-collapse.collapse
              {:id (str "comments-panel-" id)}
              [:br]
              [:ul.list-group
               (map #(vec [:li.list-group-item %]) comments)]
              [:br]
              [:div.form-group
               [:textarea.form-control
                {:type "text"
                 :ref (str "new-comment-" id)
                 :rows 3
                 :value (:modal-comment input-text)
                 :style {:resize "vertical"}
                 :on-change #(handle-text-change % owner state :modal-comment)
                 :placeholder "What do you think?"}]]
              [:button.btn.btn-primary.btn-xs
               {:type "button"
                :on-click (fn [_] (add-bookmark-comment @bookmark owner))}
               "add comment"]]]

            [:td.bookmark-date [:em.small (.toLocaleDateString date)]]

            [:td.bookmark-comments
             [:a {:href (str "#comments-panel-" id)
                  :data-parent "#bookmark-table"
                  :data-toggle "collapse"}
              [:span.badge
               {:data-toggle "tooltip"
                :data-placement "left"
                :title "Comments"}
               comment-count]]]

            [:td [:button.btn.btn-default.btn-sm
                  {:type "button"
                   :data-toggle "tooltip"
                   :data-placement "left"
                   :title "Votes"
                   :on-click #(go
                                (>! incoming
                                  (<! (post-edn "bookmark/vote" (str {:id id :upvote true})))))}
                  [:span votes]
                  " \u03BB"]]])))))


(defn update-notification [app owner value]
  (let [notify-node (om/get-node owner "center-notification")]
    (go
      (om/transact! app :notifications (fn [xs] (conj xs value)))
      (set! (.-visibility (.-style notify-node)) "visible")
      (set! (.-opacity (.-style notify-node)) "1.0")
      (<! (timeout 2000))
      (set! (.-opacity (.-style notify-node)) "0.0")
      (<! (timeout 500))
      (set! (.-visibility (.-style notify-node)) "hidden"))))


(defn bookmarks-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:incoming (chan)
       :fetch (chan)
       :notify (chan)
       :input-text {:url "" :title "" :comment "" :modal-comment ""}
       :page 0
       :page-size 9
       :counter 0})

    om/IWillMount
    (will-mount [_]
      (let [incoming (om/get-state owner :incoming)
            notify (om/get-state owner :notify)
            counter (om/get-state owner :counter)
            fetch (om/get-state owner :fetch)]
        (go
          (loop []
            (let [[v c] (alts! [incoming notify fetch])]
              (condp = c
                notify (update-notification app owner v)
                incoming (do
                           (om/transact! app :bookmarks (fn [_] (vec (sort-by :date > v))))
                           (om/set-state! owner :counter (count v)))
                fetch (fetch-url-title app owner v))
              (recur))))

        ;; welcome message
        (go
          (put! notify "Welcome to the shelf!"))

        ;; auto update bookmarks all 5 minutes
        (go
          (while true
            (.log js/console "Updating bookmarks ...")
            (>! incoming (<! (get-edn "bookmark/init")))
            (<! (timeout 3000000))))))


    om/IRenderState
    (render-state [this {:keys [incoming page page-size notify counter input-text fetch] :as state}]
      (html
       [:div
        ;; general notification container
        [:div#main-notification.notifcation-container
         [:span
          [:p {:ref "center-notification" :id "center-notification"} (last (:notifications app))]
          ]]

        ;; container input
        [:div#input-form {:role "form"}
         [:div.form-group
          [:label {:for "bookmark-url-input"} "Website"]
          [:input#bookmark-url-input.form-control
           {:type "url"
            :ref "new-url"
            :value (:url input-text)
            :placeholder "URL"
            :on-change #(handle-text-change % owner state :url)
            :onKeyPress #(when (== (.-keyCode %) 13)
                           (if (not (blank? (:url input-text)))
                             (do
                               (put! notify "Adding bookmark")
                               (add-bookmark app owner))
                             (put! notify "url input missing")))}]]

         [:label {:for "bookmark-title-input"} "Name"]
         [:div.input-group
          [:input#bookmark-title-input.form-control
           {:type "text"
            :ref "new-title"
            :value (:title input-text)
            :placeholder "Title"
            :on-change #(handle-text-change % owner state :title)
            :onKeyPress #(when (== (.-keyCode %) 13)
                           (if (not (blank? (:url input-text)))
                             (do
                               (put! notify "Adding bookmark")
                               (add-bookmark app owner))
                             (put! notify "input missing")))}]
          [:span.input-group-btn
           [:button.btn.btn-default {:type "button"
                                     :ref "fetch-btn"
                                     :data-loading-text "Fetching ..."
                                     :on-click #(if (not (blank? (:url input-text)))
                                                  (do
                                                    (put! notify "fetching title ...")
                                                    (put! fetch (:url input-text)))
                                                  (put! notify "input missing"))}
            "Fetch!"]]]

         [:br]
         [:div.form-group
          [:label {:for "bookmark-comment-input"} "Comment"]
          [:textarea#bookmark-comment-input.form-control
           {:type "text"
            :ref "new-comment"
            :value (:comment input-text)
            :rows 3
            :style {:resize "vertical"}
            :on-change #(handle-text-change % owner state :comment)
            :placeholder "Write about it ..."}]]]

        [:button.btn.btn-primary
         {:on-click #(if (not (blank? (:url input-text)))
                       (do
                         (put! notify "bookmark added")
                         (add-bookmark app owner))
                       (put! notify "input missing"))
          :type "button"
          :ref "add-btn"}
         "Add!"]

        ;; container header

        [:h2.page-header "Bookmarks"]

        ;; container list

        [:div.table-responsive
         [:table.table.table-striped
          [:tbody#bookmark-table
           (om/build-all bookmark-view (take page-size (drop (* page-size page) (:bookmarks app)))
                         {:init-state {:incoming incoming :notify notify}})]]]

        (let [page-count (/ counter page-size)]
          [:div.text-center
           [:ul.pagination
            (if (= page 0)
              [:li.disabled [:a {:href "#"} "\u00AB"]]
              [:li [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page (dec page)))} "\u00AB"]])

            (map
             #(if (= % page)
                (vec [:li.active
                      [:a {:href "#"} (inc %)
                       [:span.sr-only "(current)"]]])
                (vec [:li
                      [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page %))}
                       (inc %)]]))
             (range 0 page-count))

            (if (= page (Math/floor page-count))
              [:li.disabled [:a {:href "#"} "\u00BB"]]
              [:li [:a {:href "#" :on-click #(om/set-state! owner :page (inc page))} "\u00BB"]])]])]))))


(om/root bookmarks-view app-state {:target (. js/document (getElementById "bookmarks"))})
