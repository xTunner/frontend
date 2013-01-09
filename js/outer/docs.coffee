CI.outer.Docs = class Docs extends CI.outer.Page
  filename: (cx) =>
    name = cx.params.splat[0]
    if name
      name.replace('/', '').replace('-', '_').replace(/#.*/, '')
    else
      "docs"

  sidebar: () =>
    $("ul.topics").stickyMojo
      footerID: "#footer"
      contentID: ".article article"

  lib: () =>
    @sidebar()

  categories: (cx) =>
    # build a table of contents dynamically from all the pages. DRY.
    pages = [
              "getting-started",
              "manually",
              "common-problems",
              "configuration",
              "config-sample",
              "environment",
              "faq",
# "notifications",
#                "api"
            ]
    categories = {}
    for p in pages
      slug = p.replace("-", "_")
      template = HAML[slug]()
      node = $(template)
      title = node.find('.title > h1').text().trim()
      subtitle = node.find('.title > h4').text().trim()
      icon = node.find('.title > h1 > i').attr('class')
      section_nodes = node.find('.doc > .section > a')
      sections = []
      for s in section_nodes
        sections.push
          title: $(s).text().trim()
          hash: $(s).attr("id")
      categories[p] =
        url: "/docs/#{p}"
        slug: slug
        title: title
        subtitle: subtitle
        icon: icon
        sections: sections
    categories


  render: (cx) =>
    name = @filename cx
    if name == 'docs'
      $("body").attr("id","docs-page").html HAML['header'](renderContext)
      $("body").append HAML['docs']({categories: @categories()})
      $("body").append HAML['footer'](renderContext)
    else
      $("body").attr("id","docs-page").html(HAML['header'](renderContext))
      $("body").append(HAML['title'](renderContext))
      $("#title h1").text("Documentation")
      $("body").append("<div id='content'><section class='article'></section></div>")
      $(".article").append(HAML['categories']({categories: @categories(), page: name})).append(HAML[name](renderContext))
      $("body").append(HAML['footer'](renderContext))
