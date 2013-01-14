CI.outer.Docs = class Docs extends CI.outer.Page
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


  article_info: (slug) =>
    node = $(window.HAML[slug]())
    uriFragment = slug.replace(/_/g, '-')
    {
      url: "/docs/#{uriFragment}",
      slug: slug,
      title: node.find('.title > h1').text().trim()
      subtitle: node.find('.title > h4').text().trim()
      icon: node.find('.title > h1 > i').attr('class')
    }

  find_articles_by_tag: (tag) =>
    articles = []
    for slug of HAML
      article_tags = null

      try
        ## a bit of a hack: tagged article templates are expected to *write* into their context,
        ## and here we read what's written.
        context = {}
        window.HAML[slug](context)
        article_tags = context['article_tags']
      catch error
        ## meaning: can't be rendered without more context. Should never be true of docs!
        article_tags = null

      if article_tags
        if tag in article_tags
          articles.push(@article_info slug)
    articles

  categories: (cx) =>
    categories = {}
    for slug of HAML
      category = null

      try
        ## a bit of a hack: category templates are expected to *write* into their context,
        ## and here we read what's written.
        context = {}
        window.HAML[slug](context)
        category = context['category']
      catch error
        ## meaning: can't be rendered without more context. Should never be true of docs!
        category = null

      if category
        categories[category] = @find_articles_by_tag(category)
    categories

  title: (cx) =>
    "Documentation"

  lib: =>
    $("#query").typeahead
      'source': window.VM.suggestArticles

  render: (cx) =>
    rewrite = @rewrite_old_name cx.params.splat[0]
    if rewrite != false
      return cx.redirect "/docs" + rewrite

    name = @filename cx
    $("body").attr("id","docs-page").addClass 'outer'
    $('#main').html(HAML['header'](renderContext))
    $("#main").append HAML['title'](renderContext)
    $("#title h1").text("Documentation")
    $("#main").append("<div id='content'><section class='article'></section></div>")
    $(".article")
      .append(HAML['categories']({categories: @categories(), page: name}))
      .append(HAML[name]({find_articles_by_tag: @find_articles_by_tag})) ## XXX: merge w/renderContext?
    $("#main").append(HAML['footer'](renderContext))
