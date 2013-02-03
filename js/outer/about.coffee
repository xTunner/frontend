CI.outer.About = class About extends CI.outer.Page
  viewContext: =>
    team: [
      name: "Paul Biggar"
      role: "Founder"
      github: "pbiggar"
      twitter: "paulbiggar"
      email: "paul@circleci.com"
      photo: "paul"
      visible: true
      bio: ["Within five minutes of meeting Paul, he'll probably have mentioned that he has a PhD and did YCombinator. Given five minutes more, you'll know that his life's passions are compilers and programming languages. His proudest accomplishment is his Google Tech Talk on that topic, though it's closely followed by the time he mostly landed a backflip on skis.",
            "Paul approaches Continuous Integration from the perspective of developer productivity. It pains him to see so much time wasted due to inadequate developer tools. Good continuous integration doesn't exist yet, but it will when he's finished. Having done his PhD on static analysis of scripting languages, he has a good idea of how it should look."]
    ,
      name: "Allen Rohner"
      photo: "allen"
      visible: true
      role: "Founder"
      github: "arohner"
      twitter: "arohner"
      email: "allen@circleci.com"
      bio: ["The answer to any question posed to Allen is \"Clojure\". He <b>loves</b> Clojure! He's been working with Clojure since before it was officially released, and his name appears in the Changelog of dozens of libraries, including Clojure core. Allen's favourite philosopher is Rich Hickey, and his hobbies include Ultimate Frisbee, persistent data structures, and immutability.",
            "Allen started Circle to make Continuous Deployment available to the masses. He's greatly influenced by the work of IMVU, and his goal is their infrastructure as a generalized service. He currently deploys six times a day, and more on weekends."]
    ,
      name: "David Lowe"
      visible: true
      role: "Backend Developer"
      github: "dlowe"
      twitter: "j_david_lowe"
      email: "dlowe@circleci.com"
      photo: "david"
      bio: ["David is responsible for all of the bugs. He wrote his first buggy code in BASIC with a pencil and paper, and he's been getting better at it ever since. These days, he likes to split his time between telling kids to get off his lawn, introducing new bugs, and baking pies.",
            "David drank the automated testing kool-aid years ago. After introducing and championing (and constantly fiddling with) continuous integration tools at his last N jobs, he came to Circle to do the same on a much larger scale."]
    ,
      name: "Jenneviere Villegas"
      visible: false
      role: "Operations"
      github: "jenneviere"
      twitter: "jenneviere"
      email: "jenneviere@circleci.com"
      bio: ["Jenneviere moved to the Bay Area 3 years ago after spending a few months there, singing and dancing, as the lead in a rock opera. She then worked as an extra on a movie set, and when she realized that Mr. Coppola wasn't going to cast her as the lead in his next film, she began drowning her sorrows in hoppy IPAs and the soothing click of knitting needles. She took the black and works the Gate at That Thing In The Desert, and has only written a single line of code, ever.",
            "Jenneviere brings her many years' experience with cat wrangling to the team at Circle, and spends most of her time trying to keep everyone well-groomed and hairball free."]
    ]