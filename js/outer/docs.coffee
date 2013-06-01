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

      when "/wrong-commands" then "/wrong-ruby-commands"

      else false

  filename: (cx) =>
    name = cx.params.splat[0] or "front-page"
    name.replace(/^\//, '').replace(/\//g, '_').replace(/-/g, '_').replace(/#.*/, '')

  initialize: =>
    @articles = {}
    @categories = {}

    # process all HAML templates, and pick the articles and categories based on
    # their contents (they write into the context, and we check for that)
    for slug of HAML
      try
        # extract the metadata, which is actually in the file, writing into the context
        context = {}
        node = $(window.HAML[slug](context))
        if context.title
          @articles[slug] = @article_info(slug, node, context)
          if context.category
            @categories[slug] = @articles[slug]

      catch error
        console.log "error generating #{slug}: #{error}"
        ## meaning: can't be rendered without more context. Should never be true of docs!

    # iterate through the articles, and update the hierarchy
    for _, a of @articles
      a.children = for c in a.children
        @articles[c] or throw "Missing child article #{c}"


  article_info: (slug, node, cx) =>
    uriFragment = slug.replace(/_/g, '-')
    children = cx.children or []
    result =
      url: "/docs/#{uriFragment}"
      slug: slug
      title: cx.title or null
      children: children
      subtitle: cx.subtitle or null
      lastUpdated: cx.lastUpdated or null
      category: cx.category or null
      title_with_child_count: cx.title + (if children.length then " (#{children.length})" else "")

    unless result.category
      #console.warn "#{uriFragment} should have a subtitle" unless result.subtitle
      console.warn "#{uriFragment} must have a title" unless result.title
      console.warn "#{uriFragment} must have a lastUpdated" unless result.lastUpdated
    result

  viewContext: (cx) =>
    result =
      categories: @categories
      articles: @articles
      children: @children
      slug: @filename cx
      article: @articles[@filename cx]

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
      @addLinkTargets()

    catch e
      # TODO: go to 404 page
      return cx.redirect "/docs"


  addLinkTargets: =>
    # Add a link target to every heading. If there's an existing id, it won't override it
    for heading in $('article h2, article h3, article h4, article h5, article h6')
      @addLinkTarget heading

  addLinkTarget: (heading) =>
    jqh = $(heading)
    title = jqh.text()
    id = jqh.attr("id")

    if not id?
      id = title.toLowerCase()
      id = id.replace(/^\s+/g, '').replace(/\s+$/g, '') # strip whitespace
      id = id.replace(/\'/, '') # heroku's -> herokus
      id = id.replace(/[^a-z0-9]+/g, '-') # dashes everywhere
      id = id.replace(/^-/, '').replace(/-$/, '') # dont let first and last chars be dashes

    jqh.html("<a href='##{id}'>#{title}</a>").attr("id", id)
