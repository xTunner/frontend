# eagerly produce docs, fetching tags, and then reuse
#

CI.outer.Docs = class Docs extends CI.outer.Page
  constructor: ->
    @name = "docs"
    @initialize()

  rewrite_old_name: (name) =>
    switch name
      when "/common-problems" then ""
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
        if context.article_tags
          @articles[slug] = @article_info(slug, node, context)
        if context.category
          @categories[slug] = @category_info(slug, node, context)
      catch error
        console.log "error generating #{slug}: #{error}"
        ## meaning: can't be rendered without more context. Should never be true of docs!

    # iterate through the articles, and update the categories
    for _, a of @articles
      for t in a.article_tags
        @tags[t] or= []
        @tags[t].push a
        if @categories[t]?
          @categories[t].articles.push a


    for _, c of @categories
      c.title_with_child_count = @title_with_child_count(c.title, c.articles.length)


  article_info: (slug, node, context) =>
    uriFragment = slug.replace(/_/g, '-')
    title = node.find('.title > h1').text().trim()
    result =
      url: "/docs/#{uriFragment}"
      slug: slug
      title: title
      article_tags: context.article_tags or []
      subtitle: node.find('.title > h4').text().trim()
      lastupdated: node.find('.title > .lastupdated').text().trim()
      icon: node.find('.title > i').attr('class')
    #console.warn "#{uriFragment} should have a subtitle" unless result.subtitle
    console.warn "#{uriFragment} must have an icon" unless result.icon
    console.warn "#{uriFragment} must have a title" unless result.title
    console.warn "#{uriFragment} must have a lastupdated" unless result.lastupdated
    result

  category_info: (slug, node, context) =>
    result =
      category: context['category']
      articles: []
      slug: slug

  title_with_child_count: (title, count) ->
    if count
      title + " (#{count})"
    else
        title

  viewContext: (cx) =>
    categories: @categories
    articles: @articles
    tags: @tags
    pagename: @filename cx

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
      return cx.redirect "/docs"
