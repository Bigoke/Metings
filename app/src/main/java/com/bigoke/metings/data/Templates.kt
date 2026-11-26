package com.bigoke.metings.data

data class MeetingTemplate(
    val id: String,
    val en: String,
    val af: String,
    /** Extra summary section for this kind of meeting (blank = none). */
    val extraEn: String,
    val extraAf: String,
    /** Extra instruction for the summary AI. */
    val instruction: String,
    /** Words that mark sentences for the extra section when the summary AI is not installed. */
    val keywords: List<String>,
)

object Templates {
    val all = listOf(
        MeetingTemplate("general", "General", "Algemeen", "", "", "", emptyList()),
        MeetingTemplate(
            "board", "Board / committee", "Raad / komitee", "Resolutions", "Resolusies",
            "This is a formal board or committee meeting. Under EXTRA list each formal resolution, who proposed and seconded it, and the vote result if mentioned.",
            listOf("voorstel", "sekondeer", "stem", "resolusie", "propose", "second", "vote", "resolution", "motion", "mosie"),
        ),
        MeetingTemplate(
            "client", "Client consultation", "Kliëntkonsultasie", "Client requirements", "Kliëntbehoeftes",
            "This is a consultation with a client. Under EXTRA list what the client needs or asked for, quotes and prices given, and deadlines.",
            listOf("benodig", "wil hê", "kwotasie", "prys", "need", "want", "quote", "price", "requirement", "budget", "begroting"),
        ),
        MeetingTemplate(
            "site", "Site inspection", "Terreininspeksie", "Findings & defects", "Bevindinge en gebreke",
            "This is a site inspection. Under EXTRA list each finding, defect or safety issue with its location, and what must be fixed.",
            listOf("gebrek", "skade", "lek", "kraak", "veiligheid", "defect", "damage", "leak", "crack", "safety", "repair", "herstel"),
        ),
        MeetingTemplate(
            "staff", "Staff meeting", "Personeelvergadering", "Announcements", "Aankondigings",
            "This is a staff meeting. Under EXTRA list announcements made to staff, policy changes and dates staff must know.",
            listOf("aankondig", "beleid", "verlof", "rooster", "announce", "policy", "leave", "roster", "reminder", "herinnering"),
        ),
    )

    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
