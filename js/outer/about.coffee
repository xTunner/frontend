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
      bio: ["You might not know it by looking at him, but Paul is a wicked card shark. When he's not making his poker buddies cry, he seeks out mogul-laced ski trails to hurl himself down, and within the first five minutes of meeting him he'll probably tell you the story about how he mostly landed a backflip on his skis. Lest his ego get too big, though, we can always remind him of the time he failed at a YCombinator startup, and he'll go bury his emotions in the sweet sweet taste of artisan chocolate--or any chocolate, really. ",
            "Paul's been interviewed by the Wall Street Journal, and one of his proudest achievements was giving a Google Tech Talk on compilers and programming languages. He wrote phc, an open source PHP compiler, then did his PhD on compilers and static analysis in Dublin. Before working on the Firefox Javascript engine here in the Bay Area, he wrote stefon, an asset pipeline for Clojure. It pains Paul to see so much time wasted due to inadequate developer tools, so he approaches Continuous Integration from the perspective of developer productivity. He loves to focus on building the Circle product, but, as he'll tell you while he's walloping you at table tennis, he also has to take care of the business side of things, too. Then, while you're distracted by his work talk, he'll hit you with the smash that nearly got him booted from the San Francisco Table Tennis league, and you'll be done for."]
    ,
      name: "Allen Rohner"
      photo: "allen"
      visible: true
      role: "Founder"
      github: "arohner"
      twitter: "arohner"
      email: "allen@circleci.com"
      bio: ["Allen loves Ultimate Frisbee: he's played in the snow, on the beach, on Thanksgiving Day, New Year's Eve, and New Year's Day, and he regularly plays in 110 degree August heat. It helps take his mind off being rejected from YCombinator 4 different times, though he did get a book from Alexis Ohanian as a consolation prize.  His future plans include the tacocopter, the burrito bomber, and the mosquito laser. When Allen's not coding, he spends his time making his own pizza dough and brewing his own beer. He'd make his own whiskey, too, if he wanted to wait 10 years for a drink.",
            "Back in the day, Allen started work on his own self-hosting, native code lisp, and then the clouds parted and he discovered Clojure. Since then, he's become a Clojure contributor, and has commits in clojure.core, contrib, lein, ring, compojure, noir, and about a dozen more libraries. He finished SICP, and wrote Scriptjure, a Clojure to Javascript compiler, 2 years before ClojureScript. Other devs used his work as a basis for Stevedore, which he's happy to say is used in production every day at Circle. In addition to co-founding Circle as a means to bring Continuous Deployment to the masses, he looks forward to building tools that help debug test failures by anticipating common problems. He currently deploys six times a day, and more on weekends."]
    ,
      name: "David Lowe"
      visible: true
      role: "Backend Developer"
      github: "dlowe"
      twitter: "j_david_lowe"
      email: "dlowe@circleci.com"
      photo: "dlowe"
      bio: ["David is responsible for all of the bugs. He wrote his first buggy code in BASIC with a pencil and paper, and he's been getting better at it ever since. He keeps a 1/4 acre garden and eats almost entirely home-grown veggies during the growing season.  Helping to balance out the numerous times that he's lost at chess, David has won the IOCCC on 5 separate occasions. In a past life, he wrote MTA software which at one time was sending about 1% of the internet's email.",
            "Besides being invited to speak at the Frozen Perl Conference, OIT's CSET Department, and SOU's CS Department, David co-founded the SF perl user group, the southern Oregon geek group, and the Rogue hack lab hackerspace. He drank the automated testing kool-aid years ago, and after introducing and championing (and constantly fiddling with) continuous integration tools at his last N jobs, he came to Circle, and is particularly keen on building and scaling things which would be impossible to justify for any single small company where CI isn't what they do. He splits his time between introducing new bugs, telling kids to get off his lawn, and baking pies. His current running total is roughly 1500 delicious pastry concoctions."]
    ,
      name: "Jenneviere Villegas"
      photo: "jenn"
      visible: true
      role: "Operations"
      github: "jenneviere"
      twitter: "jenneviere"
      email: "jenneviere@circleci.com"
      bio: ["Jenneviere moved to the Bay Area in 2010 after spending a few months there, singing and dancing, as the lead in a rock opera. She then worked as an extra on the movie Twixt, and when she realized that Mr. Coppola wasn't going to cast her as the lead in his next film, she began drowning her sorrows in hoppy IPAs and the soothing click of knitting needles. She took the black and works the Gate at That Thing In The Desert, and has only written a single line of code, ever.",
            "Drawing from her many years' experience in her previous position as the Customer Amazement Specialist, Operations Manager, Returns Siren, and Retail Store Maven for a certain large utility kilt manufacturer, Jenneviere brings her skills as a professional pottymouth, dabbler in inappropriate and snarky humor, and cat wrangling to the team at Circle, and spends most of her time trying to keep everyone well-groomed and hairball free."]
    ,

      name: “Daniel Woelfel“
      photo: “daniel”
      visible: true
      role: “Backend Developer”
      github: "dwwoelfel”
      twitter: "DanielWoelfel"
      email: “daniel@circleci.com"
      bio: [“Despite all our coercions, promises, bribes, and outright threats for nearly a year, it took us putting Daniel in a time-out and forcing him to type things about himself to get a bio out of him. He doesn't really have any hobbies: if he's doing something, it's usually either reading romantic fiction (not to be confused with romance novels!), coding, or binge-watching episodes of :tv-show-he-just-discovered-that-is-so-awesome-he-can't-put-it-down. One of his first programming projects was an app written in Python that creates an exact replica of an image using only html tables. It's been almost 2 years since he last touched the thing, and it's still cranking out tables. He's only been rejected from YCombinator 3 times, which is empirically below the threshold of consolation prizes.”,
            “Daniel has a bachelors degree in Math from Texas A&M. He has written an app to graph Kickstarter projects, and was a customer (the 155th!) before he joined the Circle team. He looks forward to helping increase the "wow, I can do something cool with this!" feeling that users get when they use Circle, and has been tinkering with a few things recently to make that possible--a native websockets library so that our output feels more like you're directly connected to a terminal and hooking in something like jquery.terminal so that you actually can be directly connected to a terminal. He currently helps feed his movie habit by helping organize weekly movie nights at the office. Next up: Fight Club.”]
    ,
      name: “Mahmood Ali“
      photo: “mahmood”
      visible: true
      role: “Backend Developer”
      github: "notnoopci”
      twitter: "notnoop"
      email: “mahmood@circleci.com"
      bio: [“Mahmood loves car camping, which might explain why he also likes moving as much as he does. He and his family have moved 6 times in the last 6 years, though his baby boy has been completely lazy and hasn't offered to carry a single box yet. In between relocation to new abodes, Mahmood spends the nicer Boston weather exploring bike trails and learning computer languages. He's determined that his son will know Java before he can talk.”,
            “During his time at MIT, Mahmood spoke at a few Java research conferences including Devoxx, and has pushed code to Java 8--and Java 7--compilers. He's been a speaker at OOPSLA/Splash, and is active in the open-source community. He enjoys being reminded of his in-production projects, like java-apns, every time he gets support tickets. He acknowledges his own fallibility, and aims to help the Circle team continue to bring reliable test automation to all developers. His Achilles Heel is his pair of permanently underachieving glasses.”]
    ,

      name: “Gordon Syme“
      photo: “gordon”
      visible: true
      role: “Backend Developer”
      github: “gordonsyme”
      twitter: "gordon_syme"
      email: “gordon@circleci.com"
      bio: [“Gordon has been racing dinghies right through the winter off the coast of Ireland for the past 16-odd years, and as his oft-showcased cabinet of prize coffee mugs will tell you, he's damn good at it. When he's not busy bludgeoning a piano with his ham-fisted fingers, he spends his free time rolling down mountains on a bicycle… sometimes even the right way up. In his down time, he's a board game shark, though you won't catch him trying to buy Boardwalk or get out of jail free.”,
            “Gordon joins the team after spending significant time at Amazon building tools to monitor the entire network (he built their DWDM monitoring from the ground up). He also built a JVM bytecode recompiler to enable running applications on a clustered VM without needing re-programming effort, and has grand plans for how he's going to make waves at Circle. We're pretty sure they include company Power Grid tournaments.”]
    ,

      name: “Danny King“
      photo: “danny”
      visible: true
      role: “Designer”
      github: “dannykingme”
      twitter: “dannykingme”
      email: “danny@circleci.com"
      bio: [“Danny (not to be confused with Daniel) has batted a .500 baseball season with zero strikeouts and once took 2nd place at a poker tournament at the Mirage in Vegas. When he was 15 he, along with two of his paintball teammates, won a pickup paintball game against the three top-rated players from the number one team in the world, though he reckons this is only arguably impressive.  In the world of virtual sports, Danny has scored over a million points in Tony Hawk's Pro Skater, and has racked up massive karma points by designing a website--pro bono--for a non-profit organization that helps provide activity bags to hospitalized children. He was selected for a reality TV show on Fox but he turned it down (they're evil).”,
            “Danny finished high school at the young age of 16.  Two years after that, he cashed his first design freelance check. His past clients include Verizon, Lego, The Tribune, and Tyson Foods, and he has four individual 5-star rated projects in the Age of Empires design community. Not only does he design, but he's learned a bit of code, too, so he can build the things he designs, and his past projects include writing a tool that auto-hides Google Map's UI with simple CSS. He comes to Circle bursting with fresh ideas (like the new logo!) and can be found manipulating pixels with his hood securely in the Up position.”]
    ]
