(ns showkr.viewing
  (:require [goog.style :as style]

            [quiescent :as q :include-macros true]
            [quiescent.dom :as d]
            [keybind :as key]
            [datascript :as db]

            [showkr.utils :as u :refer-macros [p]]
            [showkr.data :as data]
            [showkr.ui :as ui]))


(defn scroll-to [el]
  (js/scroll 0 (style/getPageOffsetTop el)))

;; s  small square 75x75
;; t  thumbnail, 100 on longest side
;; m  small, 240 on longest side
;; -  medium, 500 on longest side
;; z  medium 640, 640 on longest side
;; b  large, 1024 on longest side*
(let [sizes {:small "m" :medium "z" :big "b"}]
  (defn photo-url [photo size-name]
    (apply u/fmt "http://farm%s.staticflickr.com/%s/%s_%s_%s.jpg"
      (-> ((juxt :photo/farm :photo/server :photo/id :photo/secret) photo)
        (conj (size-name sizes))))))

(defn flickr-url [photo set-id owner]
  (u/fmt "http://www.flickr.com/photos/%s/%s/in/set-%s/"
    (or (:photo/path-alias photo) owner) (:photo/id photo) set-id))

(defn flickr-avatar [{:keys [author iconfarm iconserver] :as comment}]
  (if (= iconserver "0")
    "http://www.flickr.com/images/buddyicon.jpg"
    (str "http://farm" iconfarm ".static.flickr.com/" iconserver
         "/buddyicons/" author ".jpg")))


(q/defcomponent Comment
  [{:keys [author authorname permalink datecreate content] :as comment}]
  (d/li {:className "comment"}
    (d/div {:className "avatar"}
      (d/img {:src (flickr-avatar comment)}))

    (d/div nil
      (d/a {:href (str "http://flickr.com/photos/" author)} authorname)
      (d/a {:href permalink :className "anchor"} (ui/date datecreate))
      (d/div {:className "content"
              :dangerouslySetInnerHTML (js-obj "__html" content)}))))

(q/defcomponent CommentList
  [{:keys [state comments]}]
  (case state
    nil
    (d/div {:className "span4 comments"})

    :waiting
    (d/div {:className "span4 comments"})

    :fetched
    (d/div {:className "span4 comments"}
      (apply d/ul {:className "comments"}
        (for [comment (sort-by :comment/order comments)]
          (Comment comment)))
      (d/ul {:className "pager"}))))

(q/defcomponent Photo
  [{:keys [db photo idx scroll-id set-id owner]}]
  (let [comments (:comment/_photo photo)
        upd (fn [node]
              (data/fetch-comments photo)
              (if (= (:photo/id photo) scroll-id)
                (scroll-to node)))]
    (q/wrapper
      (d/div nil
        (d/h3 nil (str (inc idx) ". " (:title photo) " ")
          (d/a {:className "anchor"
                :href (u/fmt "#%s/%s" set-id (:photo/id photo))} "#"))

        (d/small {:rel "description"} (:description photo))

        (d/div {:className "row"}
          (d/div {:className "span8"}
            (d/a {:href (flickr-url photo set-id owner)}
              (d/img {:src (photo-url photo :medium)})))
          (when comments
            (CommentList {:state (:photo/comment-state photo)
                          :comments comments}))))
      :onMount upd
      :onUpdate upd)))

(defn bind-controls! [set-id current-id]
  (key/bind! "j" ::next #(data/watch-next set-id current-id))
  (key/bind! "down" ::next #(data/watch-next set-id current-id))
  (key/bind! "space" ::next #(data/watch-next set-id current-id))
  (key/bind! "k" ::prev #(data/watch-prev set-id current-id))
  (key/bind! "up" ::prev #(data/watch-prev set-id current-id))
  (key/bind! "shift-space" ::prev #(data/watch-prev set-id current-id)))

(defn unbind-controls! []
  (key/unbind! "j" ::next)
  (key/unbind! "down" ::next)
  (key/unbind! "space" ::next)
  (key/unbind! "k" ::prev)
  (key/unbind! "up" ::prev)
  (key/unbind! "shift-space" ::prev))

(q/defcomponent Set
  [{:keys [db id scroll-id]}]
  (let [set (data/by-attr db {:id id :showkr/type :set})
        upd (fn []
              (data/fetch-set id)
              (bind-controls! id scroll-id))]

    (q/wrapper
      (case (:showkr/state set)
        :fetched
        (apply d/div nil
          (if (:title set)
            (d/h1 nil
              (d/span {:rel "title"} (:title set))))
          (d/small {:rel "description"} (:description set))

          (map-indexed
            #(Photo {:db db
                     :idx %1
                     :photo %2
                     :set-id id
                     :scroll-id scroll-id
                     :owner (:owner set)})
            (sort-by :photo/order (:photo set))))

        :waiting
        (ui/spinner)

        (d/div {:className "alert alert-error"}
          "It seems that set "
          (d/b nil id)
          " does not exist. Go to "
          (d/a {:href "#"} "index page.")))

      :onMount upd
      :onUpdate upd
      :onWillUnmount unbind-controls!)))
