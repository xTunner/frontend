<div data-bind='if: VM.docs.query_results_query'>
  <div class='article_list'>
    <div data-bind='ifnot: VM.docs.query_results().length'>

        No articles found matching
        ""

    </div>
    <div data-bind='if: VM.docs.query_results().length'>

##### 
        Articles matching
        "<span data-bind='text: VM.docs.query_results_query'></span>"

*   <a data-bind='attr: { href: url }'>
            <span data-bind='text: title'></span>
          </a>
    </div>
  </div>
</div>
<div class='row'>

#### Having problems? Check these sections

</div>