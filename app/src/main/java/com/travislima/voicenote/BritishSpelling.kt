package com.travislima.voicenote

/**
 * Converts common American spellings produced by speech recognizers to
 * British / South African spelling. A curated word map is used rather than
 * suffix rules so legal terms are never mangled (e.g. "size" must not become
 * "sise"). Case of the first letter is preserved.
 */
object BritishSpelling {

    private val map: Map<String, String> = mapOf(
        // -or -> -our
        "color" to "colour", "colors" to "colours", "colored" to "coloured", "coloring" to "colouring",
        "favor" to "favour", "favors" to "favours", "favored" to "favoured", "favorable" to "favourable",
        "favorite" to "favourite", "favorites" to "favourites",
        "honor" to "honour", "honors" to "honours", "honored" to "honoured", "honorable" to "honourable",
        "labor" to "labour", "labors" to "labours", "labored" to "laboured", "laborer" to "labourer",
        "neighbor" to "neighbour", "neighbors" to "neighbours", "neighboring" to "neighbouring",
        "behavior" to "behaviour", "behaviors" to "behaviours", "behavioral" to "behavioural",
        "endeavor" to "endeavour", "endeavors" to "endeavours",
        "harbor" to "harbour", "harbors" to "harbours",
        "rumor" to "rumour", "rumors" to "rumours",
        "vapor" to "vapour", "humor" to "humour", "odor" to "odour",
        // -ize -> -ise
        "organize" to "organise", "organizes" to "organises", "organized" to "organised",
        "organizing" to "organising", "organization" to "organisation", "organizations" to "organisations",
        "recognize" to "recognise", "recognizes" to "recognises", "recognized" to "recognised",
        "recognizing" to "recognising",
        "realize" to "realise", "realizes" to "realises", "realized" to "realised", "realizing" to "realising",
        "apologize" to "apologise", "apologized" to "apologised",
        "authorize" to "authorise", "authorized" to "authorised", "authorization" to "authorisation",
        "criticize" to "criticise", "criticized" to "criticised", "criticism" to "criticism",
        "emphasize" to "emphasise", "emphasized" to "emphasised",
        "finalize" to "finalise", "finalized" to "finalised", "finalization" to "finalisation",
        "formalize" to "formalise", "formalized" to "formalised",
        "generalize" to "generalise", "generalized" to "generalised",
        "initialize" to "initialise", "initialized" to "initialised",
        "legalize" to "legalise", "legalized" to "legalised",
        "memorize" to "memorise", "memorized" to "memorised",
        "minimize" to "minimise", "minimized" to "minimised",
        "maximize" to "maximise", "maximized" to "maximised",
        "penalize" to "penalise", "penalized" to "penalised",
        "prioritize" to "prioritise", "prioritized" to "prioritised",
        "scrutinize" to "scrutinise", "scrutinized" to "scrutinised",
        "specialize" to "specialise", "specialized" to "specialised",
        "summarize" to "summarise", "summarized" to "summarised",
        "utilize" to "utilise", "utilized" to "utilised",
        // -yze -> -yse
        "analyze" to "analyse", "analyzes" to "analyses", "analyzed" to "analysed", "analyzing" to "analysing",
        "paralyze" to "paralyse", "paralyzed" to "paralysed",
        // -er -> -re
        "center" to "centre", "centers" to "centres", "centered" to "centred",
        "meter" to "metre", "meters" to "metres",
        "liter" to "litre", "liters" to "litres",
        "fiber" to "fibre", "fibers" to "fibres",
        "theater" to "theatre", "theaters" to "theatres",
        // -se/-ce and misc (legal-relevant)
        "defense" to "defence", "defenses" to "defences",
        "offense" to "offence", "offenses" to "offences",
        "pretense" to "pretence",
        "license" to "licence", "licenses" to "licences",
        "practicing" to "practising", "practiced" to "practised",
        "judgment" to "judgment", // both accepted in SA courts; leave as dictated
        "counselor" to "counsellor", "counselors" to "counsellors",
        "traveling" to "travelling", "traveled" to "travelled", "traveler" to "traveller",
        "canceled" to "cancelled", "canceling" to "cancelling", "cancelation" to "cancellation",
        "labeled" to "labelled", "labeling" to "labelling",
        "modeled" to "modelled", "modeling" to "modelling",
        "fulfill" to "fulfil", "fulfillment" to "fulfilment",
        "enrollment" to "enrolment", "installment" to "instalment",
        "skillful" to "skilful", "willful" to "wilful",
        "gray" to "grey", "plow" to "plough",
        "check" to "check", // never touch (cheque only when meaning payment; can't know)
        "program" to "programme", // SA usage for schedules/proceedings; computer sense is rare in dictation
        "catalog" to "catalogue", "dialog" to "dialogue",
        "acknowledgment" to "acknowledgement",
        "aging" to "ageing",
        "maneuver" to "manoeuvre", "maneuvers" to "manoeuvres",
        "estrogen" to "oestrogen", "pediatric" to "paediatric",
        "anesthetic" to "anaesthetic",
        "jewelry" to "jewellery",
        "mold" to "mould", "molds" to "moulds",
        "smolder" to "smoulder",
        "tire" to "tyre", // vehicle sense; verb "tire" is uncommon in legal dictation
        "curb" to "kerb",
        "draft" to "draft", // never becomes draught in legal text
        "inquiry" to "enquiry",
        "specialty" to "speciality",
        "toward" to "towards", "afterward" to "afterwards",
        "amid" to "amidst", "among" to "among",
    )

    /** Normalise a single word, preserving leading capitalisation. */
    fun normaliseWord(word: String): String {
        val lower = word.lowercase()
        val replacement = map[lower] ?: return word
        return if (word.firstOrNull()?.isUpperCase() == true) {
            replacement.replaceFirstChar { it.uppercase() }
        } else replacement
    }

    /** Normalise every word in a phrase, leaving punctuation untouched. */
    fun normalise(text: String): String =
        Regex("[A-Za-z]+").replace(text) { m -> normaliseWord(m.value) }
}
