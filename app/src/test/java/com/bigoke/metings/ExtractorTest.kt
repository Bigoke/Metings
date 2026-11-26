package com.bigoke.metings

import com.bigoke.metings.data.EntityType
import com.bigoke.metings.data.Utterance
import com.bigoke.metings.engine.EntityExtractor
import com.bigoke.metings.engine.NumberWords
import com.bigoke.metings.engine.TextCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExtractorTest {

    private val meetingTime = LocalDate.of(2026, 11, 26).atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun run(vararg texts: String) = EntityExtractor(meetingTime)
        .extract(texts.mapIndexed { i, t -> Utterance(i % 2, i * 1000L, i * 1000L + 900, t) }, mapOf(0 to "Person 1", 1 to "Person 2"))
        .also { list -> list.forEach { println("${it.type}  '${it.text}'  -> ${it.value}") } }

    @Test fun numberWords() {
        assertEquals(25L, NumberWords.wordValue("vyf-en-twintig"))
        assertEquals(25L, NumberWords.wordValue("vyfentwintig"))
        assertEquals(200L, NumberWords.wordValue("tweehonderd"))
        assertEquals(250L, NumberWords.wordValue("tweehonderdvyftig"))
        assertEquals(1500.0, NumberWords.parseDigits("1 500")!!, 0.001)
        assertEquals(1500.5, NumberWords.parseDigits("1 500,50")!!, 0.001)
        assertEquals(1.5, NumberWords.parseDigits("1,5")!!, 0.001)
    }

    @Test fun afrikaansMeeting() {
        val e = run(
            "Goeiemôre almal. Die begroting vir die nuwe kantoor in Pretoria is 200 duisend rand.",
            "Jan sal die verslag teen volgende Vrydag stuur. Die mure moet blou geverf word.",
            "Ons vergader weer op 15 Maart 2027 om 14:30 by Kerkstraat 12, Stellenbosch.",
            "Bel vir Marietjie by 082 555 1234 of stuur 'n e-pos na marietjie at gmail dot com.",
            "Die tafel is 'n ronde tafel van 2,4 meter en kos R12 500. Ons het vyf-en-twintig stoele nodig.",
        )
        assertTrue(e.any { it.type == EntityType.AMOUNT && it.value == "ZAR 200000.00" })
        assertTrue(e.any { it.type == EntityType.PLACE && it.value == "Pretoria" })
        assertTrue(e.any { it.type == EntityType.PERSON && it.text == "Jan" })
        assertTrue(e.any { it.type == EntityType.DATE && it.value == "2026-12-04" })
        assertTrue(e.any { it.type == EntityType.COLOUR && it.text == "blou" })
        assertTrue(e.any { it.type == EntityType.DATE && it.value == "2027-03-15" })
        assertTrue(e.any { it.type == EntityType.TIME && it.value == "14:30" })
        assertTrue(e.any { it.type == EntityType.ADDRESS && it.text.startsWith("Kerkstraat 12") })
        assertTrue(e.any { it.type == EntityType.PHONE && it.value == "+27825551234" })
        assertTrue(e.any { it.type == EntityType.EMAIL && it.value == "marietjie@gmail.com" })
        assertTrue(e.any { it.type == EntityType.SHAPE })
        assertTrue(e.any { it.type == EntityType.MEASUREMENT && it.text.contains("2,4 meter") })
        assertTrue(e.any { it.type == EntityType.AMOUNT && it.value == "ZAR 12500.00" })
        assertTrue(e.any { it.type == EntityType.NUMBER && it.value == "25" })
        assertTrue(e.any { it.type == EntityType.TASK && it.value == "Jan|2026-12-04" })
    }

    @Test fun lowercaseTranscript() {
        val e = run("jan sal die verslag stuur na die kantoor in pretoria teen môre")
        assertTrue(e.any { it.type == EntityType.PERSON && it.value == "Jan" })
        assertTrue(e.any { it.type == EntityType.PLACE && it.value == "Pretoria" })
        assertTrue(e.any { it.type == EntityType.DATE && it.value == "2026-11-27" })
    }

    @Test fun englishMeeting() {
        val e = run(
            "The budget for the Johannesberg branch is R2.5 million, about 15% more than last year.",
            "Sarah will send the report by Friday. Can you call John Smith on +27 21 555 0199?",
            "The logo should be dark blue and the sign must be 3 m x 2 m.",
        )
        assertTrue(e.any { it.type == EntityType.PLACE && it.value == "Johannesburg" })
        assertTrue(e.any { it.type == EntityType.AMOUNT && it.value == "ZAR 2500000.00" })
        assertTrue(e.any { it.type == EntityType.PERCENT && it.value == "15%" })
        assertTrue(e.any { it.type == EntityType.PERSON && it.value == "John Smith" })
        assertTrue(e.any { it.type == EntityType.COLOUR && it.text == "dark blue" })
        assertTrue(e.any { it.type == EntityType.MEASUREMENT })
        assertTrue(e.any { it.type == EntityType.TASK && it.value.startsWith("Sarah|") })
    }

    @Test fun cleaner() {
        val t = TextCleaner.clean("uhm die die mensen gaan eh môre na die gebouw toe", "af", true)
        println(t)
        assertEquals("Die mense gaan môre na die gebou toe.", t)
        val t2 = TextCleaner.clean("'n paar dinge. ons begin nou", "af", true)
        println(t2)
        assertEquals("'n Paar dinge. Ons begin nou.", t2)
    }
}
