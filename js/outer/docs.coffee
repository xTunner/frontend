# eagerly produce docs, fetching tags, and then reuse
#

CI.outer.Docs = class Docs extends CI.outer.Page
  constructor: ->
    @name = "docs"
    @initialize()

  rewrite_old_name: (name) =>
    switch name
      when "/common-problems#intro" then ""
      when "/common-problems#file-ordering" then "/file-ordering"
      when "/common-problems#missing-log-dir" then "/missing-log-dir"
      when "/common-problems#missing-file" then "/missing-file"
      when "/common-problems#time-day" then "/time-day"
      when "/common-problems#time-seconds" then "/time-seconds"
      when "/common-problems#requires-admin" then "/requires-admin"
      when "/common-problems#oom" then "/oom"
      when "/common-problems#wrong-ruby-version" then "/wrong-ruby-version"
      when "/common-problems#dont-run" then "/dont-run"
      when "/common-problems#git-bundle-install" then "/git-bundle-install"
      when "/common-problems#git-pip-install" then "/git-pip-install"
      when "/common-problems#wrong-commands" then "/wrong-commands"
      when "/common-problems#bundler-latest" then "/bundler-latest"
      when "/common-problems#capybara-timeout" then "/capybara-timeout"
      when "/common-problems#clojure-12" then "/clojure-12"
      when "/common-problems" then "/troubleshooting"

      when "/faq" then ""
      when "/faq#permissions" then "/permissions"
      when "/faq#what-happens" then "/what-happens"
      when "/faq#look-at-code" then "/look-at_code"
      when "/faq#parallelism" then "/parallelism"
      when "/faq#versions" then "/environment"
      when "/faq#external-resources" then "/external-resources"
      when "/faq#cant-follow" then "/cant-follow"

      else false

  filename: (cx) =>
    name = cx.params.splat[0] or "front-page"
    name.replace(/^\//, '').replace(/\//g, '_').replace(/-/g, '_').replace(/#.*/, '')

  initialize: =>
    @articles = {}
    @categories = {}
    @tags = {}

    # process all HAML templates, and pick the articles and categories based on
    # their contents (they write into the context, and we check for that)
    for slug of HAML
      try
        # extract the metadata, which is actually in the file, writing into the context
        context = {}
        node = $(window.HAML[slug](context))
        if context.category
          @categories[slug] = @category_info(slug, node, context)
          @articles[slug] = @article_info(slug, node, context)
          @categories[slug].article = @articles[slug]
          console.log "adding #{slug} to categories"
        else if context.title
          @articles[slug] = @article_info(slug, node, context)
      catch error
        console.log "error generating #{slug}: #{error}"
        ## meaning: can't be rendered without more context. Should never be true of docs!

    # iterate through the articles, and update the hierarchy
    for _, a of @articles
      for t in a.parents
        @tags[t] or= []
        @tags[t].push a


    for _, i of $.extend({}, @categories, @articles)
      i.title_with_child_count = @title_with_child_count(i.title, @tags[i.slug]?.length)


  article_info: (slug, node, context) =>
    uriFragment = slug.replace(/_/g, '-')
    result =
      url: "/docs/#{uriFragment}"
      slug: slug
      title: context.title or null
      parents: context.parents or []
      subtitle: context.subtitle or null
      lastUpdated: context.lastUpdated or null
      icon: context.icon or null
      category: context.category or null

    unless result.category
      #console.warn "#{uriFragment} should have a subtitle" unless result.subtitle
      console.warn "#{uriFragment} must have an icon" unless result.icon
      console.warn "#{uriFragment} must have a title" unless result.title
      console.warn "#{uriFragment} must have a lastUpdated" unless result.lastUpdated
    result

  category_info: (slug, node, context) =>
    result =
      category: context.category
      slug: slug

  title_with_child_count: (title, count) ->
    if count
      title + " (#{count})"
    else
      title

  viewContext: (cx) =>
    result =
      categories: @categories
      articles: @articles
      tags: @tags
      pagename: @filename cx
    result = $.extend result, @articles[@filename cx]
    result

  title: (cx) =>
    try
      @articles[@filename(cx)].title
    catch e
      null

  render: (cx) =>
    try
      rewrite = @rewrite_old_name cx.params.splat[0]
      if rewrite != false
        return cx.redirect "/docs" + rewrite

      super cx

    catch e
      # TODO: go to 404 page
      return cx.redirect "/docs"
