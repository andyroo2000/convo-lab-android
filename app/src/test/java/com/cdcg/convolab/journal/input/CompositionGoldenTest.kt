package com.cdcg.convolab.journal.input

import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class CompositionGoldenTest {
    @Test fun typingEditingChoicesAndSectionsMatchTheIosStateMachine() {
        val factory = DocumentBuilderFactory.newInstance().apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val document = checkNotNull(javaClass.getResourceAsStream("/typing/composition-golden.xml")).use { factory.newDocumentBuilder().parse(it) }
        val scenarios = document.documentElement.children("scenario")
        assertEquals(5, scenarios.size)
        for (scenario in scenarios) {
            val composition = JournalComposition()
            scenario.children("event").forEachIndexed { index, event ->
                val actual = applyEvent(composition, event)
                assertSnapshot("${scenario.value("name")} event $index", event.children("expected").single(), actual)
            }
        }
    }
    private fun applyEvent(composition: JournalComposition, event: Element): InputSnapshot = when (event.value("kind")) {
        "edit" -> composition.edit(InputEdit(event.number("start"), event.number("length"), event.value("replacement")),
            InputSelection(event.number("sel"), event.number("sel") + event.number("selLength")), event.value("literal").toBooleanStrict())
        "move" -> composition.moveSelection(InputSelection(event.number("start"), event.number("start") + event.number("length")))
        "external" -> composition.external(event.value("text"))
        "receive" -> receive(composition, event)
        else -> command(composition, event)
    }
    private fun command(composition: JournalComposition, event: Element): InputSnapshot = when (event.value("kind")) {
        "commit" -> composition.commit()
        "choose" -> composition.choose(event.number("value"))
        "focusSection" -> composition.focusSection(event.number("value"))
        "resize" -> composition.resize(event.number("value"))
        "cycle" -> composition.cycle()
        "accept" -> composition.accept()
        else -> error("Unexpected fixture command")
    }
    private fun receive(composition: JournalComposition, event: Element): InputSnapshot {
        val query = composition.snapshot().query ?: return composition.snapshot()
        val candidates = event.children("candidate").map { candidate -> JapaneseCandidate(candidate.value("text"), candidate.value("reading"),
            parts(candidate, "sections"), parts(candidate, "units")) }
        return composition.receive(query.copy(reading = event.value("reading")), candidates)
    }
    private fun parts(candidate: Element, kind: String) = candidate.children(kind).single().children("part").map { CandidatePart(it.value("text"), it.value("reading")) }
    private fun assertSnapshot(message: String, expected: Element, actual: InputSnapshot) {
        val values = mapOf("text" to actual.text, "caret" to actual.selection.end.toString(), "composition" to range(actual.composition),
            "section" to range(actual.section), "reading" to actual.query?.reading.orEmpty(), "highlighted" to actual.highlighted.toString(),
            "boundaries" to actual.query?.boundaries.orEmpty().joinToString(","))
        values.forEach { (key, value) -> assertEquals("$message $key", expected.value(key), value) }
        assertEquals("$message choices", expected.children("choice").map { it.value("text") }, actual.choices)
    }
    private fun range(value: InputSelection?) = value?.let { "${it.start},${it.end - it.start}" }.orEmpty()
}
private fun Element.children(tag: String) = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == tag }
private fun Element.value(key: String) = String(Base64.getDecoder().decode(getAttribute(key)), Charsets.UTF_8)
private fun Element.number(key: String) = value(key).toInt()
