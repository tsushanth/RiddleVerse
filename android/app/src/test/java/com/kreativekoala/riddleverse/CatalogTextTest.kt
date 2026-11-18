package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every card, tagline, label, filter and collection the catalog shows must have a string resource. */
class CatalogTextTest {

    @Test fun everyCardHasTitleAndSubtitleResources() {
        for (c in QuizCategories.allCategories) {
            assertNotEquals("title res for ${c.category}", 0, CatalogText.titleRes(c.category))
            assertNotEquals("subtitle res for ${c.category}", 0, CatalogText.subtitleRes(c.category))
        }
    }

    @Test fun typeLookupIsCaseInsensitive() {
        assertEquals(CatalogText.titleRes("storyPuzzle"), CatalogText.titleRes("storypuzzle"))
        assertNotEquals(0, CatalogText.titleRes("sentenceTransitions"))
    }

    @Test fun everyFilterAndSkillLabelHasAResource() {
        for (c in QuizCategories.allCategories) {
            assertNotEquals("filter res for ${c.filterCategory}", 0, CatalogText.filterRes(c.filterCategory))
            assertNotEquals("label res for ${c.filterCategory}", 0, CatalogText.labelRes(PuzzleTypeNames.labelForFilter(c.filterCategory)))
        }
        assertEquals(0, CatalogText.filterRes("All"))
    }

    @Test fun newGamesHaveBenefitsAndTaglines() {
        for (c in QuizCategories.allCategories) {
            val info = NewGameInfo.of(c.category) ?: continue
            assertEquals("benefits for ${c.category}", 2, CatalogText.benefitRes(c.category)?.size)
            assertNotEquals("label res for ${info.category}", 0, CatalogText.labelRes(info.category))
        }
        for (type in DiscoveryCatalog.TAGLINES.keys) assertNotEquals("tagline res for $type", 0, CatalogText.taglineRes(type))
    }

    @Test fun everyCollectionHasAName() {
        val groups = HardcodedPuzzleGroups.getAllGroups()
        assertTrue(groups.isNotEmpty())
        for (g in groups) assertNotEquals("name res for ${g.id}", 0, CatalogText.groupNameRes(g.id))
    }

    @Test fun legacyKeysHaveNames() {
        for (key in listOf("wordassociation", "imagematch", "find_differences", "flowpuzzle", "multiple choice", "q&a", "daily", "custom")) {
            assertNotEquals("extra name res for $key", 0, CatalogText.extraNameRes(key))
        }
        assertNotNull(PuzzleTypeNames.display("daily"))
    }
}
