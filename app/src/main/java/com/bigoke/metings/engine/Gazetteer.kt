package com.bigoke.metings.engine

/** Offline word lists used by the detail finder. Afrikaans and English. */
object Gazetteer {

    private const val PLACES_RAW = """
Johannesburg|Joburg|Jozi|Egoli;Kaapstad|Cape Town;Pretoria|Tshwane;Durban|eThekwini;Bloemfontein|Mangaung;
Gqeberha|Port Elizabeth|PE;Oos-Londen|East London;Pietermaritzburg;Polokwane|Pietersburg;Mbombela|Nelspruit;
Kimberley;Rustenburg;Stellenbosch;Paarl;Mosselbaai|Mossel Bay;Knysna;Upington;Worcester;Potchefstroom;
Klerksdorp;Vereeniging;Vanderbijlpark;Centurion;Midrand;Sandton;Soweto;Benoni;Boksburg;Springs;Germiston;
Kempton Park;Krugersdorp;Randburg;Roodepoort;eMalahleni|Witbank;Middelburg;Secunda;Ermelo;Standerton;Bethlehem;
Welkom;Kroonstad;Sasolburg;Parys;Harrismith;Ladysmith;Newcastle;Richardsbaai|Richards Bay;Ballito;Umhlanga;
Pinetown;Margate;Kokstad;Mthatha|Umtata;Makhanda|Grahamstown;Graaff-Reinet;Oudtshoorn;Beaufort-Wes|Beaufort West;
Hermanus;Swellendam;Bredasdorp;Langebaan;Saldanha;Vredenburg;Malmesbury;Wellington;Franschhoek;
Somerset-Wes|Somerset West;Strand;Bellville;Durbanville;Brackenfell;Kuilsrivier;Mitchells Plain;Khayelitsha;
Springbok;Calvinia;Vryburg;Mahikeng|Mafikeng;Lichtenburg;Brits;Hartbeespoort;Bela-Bela|Warmbad;Mokopane|Potgietersrus;
Thohoyandou;Tzaneen;Phalaborwa;Musina|Messina;Lephalale|Ellisras;Hoedspruit;Witrivier|White River;Barberton;
Komatipoort;Vryheid;Dundee;Estcourt;Howick;Port Shepstone;Jeffreysbaai|Jeffreys Bay;Plettenbergbaai|Plettenberg Bay|Plett;
Kariega|Uitenhage;Komani|Queenstown;Aliwal-Noord|Aliwal North;De Aar;Colesberg;Kuruman;Postmasburg;Kathu;Prieska;
Carnarvon;Sutherland;Clanwilliam;Citrusdal;Piketberg;Robertson;Montagu;Ceres;Tulbagh;Riebeek-Kasteel;Atlantis;
Lydenburg|Mashishing;Bronkhorstspruit;Cullinan;Heidelberg;Meyerton;Alberton;Edenvale;Fourways;Bryanston;
Rosebank;Melville;Hatfield;Menlo Park;Waterkloof;Garsfontein;Moreleta Park;Sinoville;Akasia;
Soshanguve;Mamelodi;Atteridgeville;Tembisa;Alexandra;Lenasia;Fochville;Carletonville;Westonaria;Randfontein;
Gauteng;Wes-Kaap|Western Cape;Oos-Kaap|Eastern Cape;Noord-Kaap|Northern Cape;Vrystaat|Free State;
KwaZulu-Natal|KZN|Natal;Limpopo;Mpumalanga;Noordwes|North West;Karoo;Boland;Overberg;Weskus|West Coast;
Suid-Afrika|South Africa|SA|RSA;Namibie|Namibia;Botswana;Zimbabwe;Mosambiek|Mozambique;Lesotho;Eswatini|Swaziland;
Zambie|Zambia;Angola;Kenia|Kenya;Nigerie|Nigeria;Engeland|England;Brittanje|Britain;Verenigde Koninkryk|United Kingdom|UK;
Amerika|America|USA;Verenigde State|United States;Australie|Australia;Nieu-Seeland|New Zealand;Nederland|Netherlands|Holland;
Belgie|Belgium;Duitsland|Germany;Frankryk|France;Spanje|Spain;Portugal;Italie|Italy;Switserland|Switzerland;Kanada|Canada;
Sjina|China;Japan;Indie|India;Mauritius;Ierland|Ireland;Skotland|Scotland;Griekeland|Greece;Egipte|Egypt;
Londen|London;Paris;New York;Amsterdam;Berlyn|Berlin;Dubai;Sydney;Perth;Melbourne;Brisbane;Auckland;
Toronto;Vancouver;Windhoek;Walvisbaai|Walvis Bay;Swakopmund;Gaborone;Maputo;Harare;Bulawayo;Maseru;Mbabane;Lusaka;
Nairobi;Lagos;Kairo|Cairo;Rome;Madrid;Lissabon|Lisbon;Brussel|Brussels;Munchen|Munich;Frankfurt;Zurich;
Singapoer|Singapore;Hong Kong;Tokio|Tokyo;Beijing;Mumbai;Doha;Abu Dhabi;Los Angeles;Chicago;Houston;Miami;Orlando;
Edinburgh;Manchester;Birmingham;Dublin;Wenen|Vienna;Praag|Prague;Kopenhagen|Copenhagen;Stockholm;Oslo;Helsinki
"""

    /** folded alias -> canonical display name */
    val places: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        PLACES_RAW.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            val names = entry.split('|').map { it.trim() }
            names.forEach { m[Fuzzy.fold(it)] = names.first() }
        }
        m
    }
    val placeSingleWords: Set<String> by lazy { places.keys.filter { ' ' !in it && it.length >= 6 }.toSet() }
    val maxPlaceWords = 3

    private const val NAMES_RAW = """
Jan Piet Koos Hennie Johan Johann Johannes Gerhard Gerrit Gert Andre Andries Danie Daniel Dawie David Deon Dirk Eben
Francois Frans Frik Hendrik Herman Hugo Jaco Jacobus Jakes Jannie Jean Jurie Kobus Louis Louw Marius Martin Morne
Nico Niel Pieter Petrus Philip Pierre Riaan Rian Ruan Rudi Schalk Stefan Stephan Theo Theuns Tiaan Wessel Willem
Wikus Werner Wynand Christo Christiaan Charl Cobus Corne Danie Fanie Faan Gideon Hannes Ivan Jacques Juan Kallie
Anton Arno Barend Bernard Braam Carel Chris Coenie Ernst Etienne Ferdi Flip Gawie Gustav Henk Henning Japie Jasper
Anna Annelie Anneke Annemarie Carla Carien Chantel Charlene Christa Elize Elsabe Elmarie Esme Hanlie Hannelie
Ilse Ilze Ina Inge Jana Janine Jeanette Johanna Karin Karen Leane Liezl Lize Lizette Magda Maria Marietjie Marlize
Melanie Mariska Mia Michelle Mona Nadia Nadine Nelia Petro Retha Riana Ronel Rina Sanet Sanette Sonja Suzanne Tanya
Tania Thea Ursula Wilma Yolandi Yolande Zelda Zanele Annette Marike Marelize Lindie Nicolene Nicolette Elna Estie
John James Michael Mike Peter Paul Mark Matthew Andrew Robert Bob Richard William Bill Thomas Tom George Edward
Steven Steve Kevin Brian Jason Jonathan Ryan Sean Shaun Gary Craig Grant Bruce Dean Neil Greg Gregory Trevor Wayne
Mary Sarah Sara Jennifer Jenny Lisa Linda Susan Karen Nicole Nikki Emma Olivia Sophie Rachel Laura Kate Katie
Jessica Megan Amy Lauren Hannah Natalie Samantha Sam Kim Kelly Lee Leigh Claire Clare Fiona Heather Helen Julie
Thabo Sipho Themba Bongani Mandla Mpho Lerato Palesa Nomsa Thandi Thandeka Zodwa Sibusiso Lwazi Kagiso Tshepo
Katlego Neo Naledi Lindiwe Nandi Ayanda Andile Siyabonga Sizwe Vusi Xolani Busisiwe Nokuthula Zinhle Precious
Tumelo Lebo Lebogang Mosa Refilwe Dineo Puleng Khanyisile Nkosi Musa Jabu Jabulani Sello Tebogo Thulani Zama
Mohammed Ahmed Fatima Ayesha Yusuf Ismail Rashid Priya Ravi Pravin Anil Sunil Deepak Nisha
Botha Nel Pretorius Van der Merwe Du Plessis Venter Smit Smith Coetzee Fourie Steyn Le Roux Kruger Viljoen
Jacobs Erasmus Engelbrecht Joubert Swanepoel Van Wyk Vermeulen Visser Marais Potgieter Strydom Oosthuizen
Louw Meyer Nortje Olivier Roos Schoeman Theron Van Zyl Mostert Malan Naude Badenhorst Booysen Cilliers
"""

    val firstNames: Set<String> by lazy {
        NAMES_RAW.split(Regex("\\s+")).filter { it.isNotBlank() }.map { Fuzzy.fold(it) }.toSet()
    }

    val titles = setOf(
        "mnr", "mev", "mej", "dr", "prof", "ds", "oom", "tannie", "mr", "mrs", "ms", "miss", "sir", "adv", "kapt", "kol",
    )

    val months: Map<String, Int> = mapOf(
        "januarie" to 1, "januari" to 1, "january" to 1, "jan" to 1,
        "februarie" to 2, "februari" to 2, "february" to 2, "feb" to 2,
        "maart" to 3, "march" to 3, "mar" to 3, "mrt" to 3,
        "april" to 4, "apr" to 4,
        "mei" to 5, "may" to 5,
        "junie" to 6, "juni" to 6, "june" to 6, "jun" to 6,
        "julie" to 7, "juli" to 7, "july" to 7, "jul" to 7,
        "augustus" to 8, "august" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9,
        "oktober" to 10, "october" to 10, "okt" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "desember" to 12, "december" to 12, "des" to 12, "dec" to 12,
    )

    /** ISO day of week 1=Monday. */
    val weekdays: Map<String, Int> = mapOf(
        "maandag" to 1, "monday" to 1, "dinsdag" to 2, "tuesday" to 2, "woensdag" to 3, "wednesday" to 3,
        "donderdag" to 4, "thursday" to 4, "vrydag" to 5, "friday" to 5, "saterdag" to 6, "saturday" to 6,
        "sondag" to 7, "sunday" to 7,
    )

    val colours: Map<String, String> = mapOf(
        "rooi" to "#E53935", "red" to "#E53935", "blou" to "#1E88E5", "blue" to "#1E88E5",
        "groen" to "#43A047", "green" to "#43A047", "geel" to "#FDD835", "yellow" to "#FDD835",
        "oranje" to "#FB8C00", "orange" to "#FB8C00", "pers" to "#8E24AA", "purple" to "#8E24AA",
        "pienk" to "#EC407A", "pink" to "#EC407A", "swart" to "#111111", "black" to "#111111",
        "wit" to "#FFFFFF", "white" to "#FFFFFF", "grys" to "#9E9E9E", "grey" to "#9E9E9E", "gray" to "#9E9E9E",
        "bruin" to "#6D4C41", "brown" to "#6D4C41", "goud" to "#D4AF37", "gold" to "#D4AF37",
        "silwer" to "#C0C0C0", "silver" to "#C0C0C0", "beige" to "#E8DCC0", "turkoois" to "#30C5C0",
        "turquoise" to "#30C5C0", "maroen" to "#800000", "maroon" to "#800000", "vlootblou" to "#1A237E",
        "navy" to "#1A237E", "violet" to "#7F00FF", "magenta" to "#D81B60", "siaan" to "#00BCD4", "cyan" to "#00BCD4",
        "kakie" to "#C3B091", "khaki" to "#C3B091", "koper" to "#B87333", "copper" to "#B87333",
        "brons" to "#CD7F32", "bronze" to "#CD7F32", "lila" to "#C8A2C8", "lilac" to "#C8A2C8",
        "perske" to "#FFCBA4", "peach" to "#FFCBA4", "olyfgroen" to "#708238", "olive" to "#708238",
        "blougroen" to "#008080", "teal" to "#008080", "donkerblou" to "#0D47A1", "ligblou" to "#81D4FA",
        "donkergroen" to "#1B5E20", "liggroen" to "#AED581", "donkerrooi" to "#8B0000", "roomkleur" to "#FFFDD0",
        "cream" to "#FFFDD0", "charcoal" to "#36454F", "houtskool" to "#36454F",
    )
    val colourModifiers = setOf("donker", "lig", "dark", "light", "bright", "helder", "pale", "bleek")

    val shapes: Map<String, String> = mapOf(
        "sirkel" to "circle", "circle" to "circle", "rond" to "circle", "ronde" to "circle", "round" to "circle",
        "vierkant" to "square", "vierkantig" to "square", "vierkantige" to "square", "square" to "square",
        "reghoek" to "rectangle", "reghoekig" to "rectangle", "reghoekige" to "rectangle", "rectangle" to "rectangle",
        "rectangular" to "rectangle", "driehoek" to "triangle", "driehoekig" to "triangle", "driehoekige" to "triangle",
        "triangle" to "triangle", "triangular" to "triangle", "ovaal" to "oval", "ovale" to "oval", "oval" to "oval",
        "ster" to "star", "star" to "star", "kubus" to "cube", "cube" to "cube", "sfeer" to "sphere",
        "sphere" to "sphere", "piramide" to "pyramid", "pyramid" to "pyramid", "silinder" to "cylinder",
        "cylinder" to "cylinder", "keel" to "cone", "cone" to "cone", "seshoek" to "hexagon", "hexagon" to "hexagon",
        "agthoek" to "octagon", "octagon" to "octagon", "vyfhoek" to "pentagon", "pentagon" to "pentagon",
        "spiraal" to "spiral", "spiral" to "spiral", "ellips" to "ellipse", "ellipse" to "ellipse",
        "l-vorm" to "L-shape", "l-shape" to "L-shape", "u-vorm" to "U-shape", "u-shape" to "U-shape",
    )

    val stopCapitalised: Set<String> by lazy {
        (
            """ek jy hy sy ons julle hulle die 'n en maar of want so dan toe nou ja nee goed dankie baie ook nog net al
            dit dat wat wie waar wanneer hoe hoekom is was het sal kan moet wil gaan kom sien sê weet dink okay ok oukei
            i you he she we they it the a an and but or so then now yes no well thanks thank please also just
            this that what who where when how why is was has have will can must want go come see say know think
            hallo hello hi goeiemore goeiemiddag goeienaand morning afternoon evening dag mister meneer mevrou
            persoon person speaker spreker vergadering meeting agenda item punt""".split(Regex("\\s+"))
        ).map { Fuzzy.fold(it) }.filter { it.isNotEmpty() }.toSet()
    }
}
