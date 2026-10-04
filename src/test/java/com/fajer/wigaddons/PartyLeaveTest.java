package com.fajer.wigaddons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PartyLeaveTest {
	private static boolean matches(String line) {
		return PartyLeave.matches(line, PartyLeave.DEFAULT_PATTERNS);
	}

	@Test
	void recognisesTheServersPartyNotices() {
		assertTrue(matches("Davidcovi has left the party."));
		assertTrue(matches("[MVP+] Davidcovi has left the party."));
		assertTrue(matches("[VIP] [MVP++] Davidcovi has left the party."));
		assertTrue(matches("Davidcovi was removed from the party because they were offline."));
		assertTrue(matches("[MVP+] Davidcovi has been removed from the party."));
		assertTrue(matches("Davidcovi has been kicked from the party."));
		assertTrue(matches("The party was disbanded because all invites expired and the party was empty."));
	}

	@Test
	void formattingCodesAreIgnored() {
		assertTrue(matches("\u00a7b[MVP\u00a7c+\u00a7b] Davidcovi \u00a7ehas left the party.\u00a7r"));
	}

	@Test
	void caseDoesNotMatter() {
		assertTrue(matches("davidcovi HAS LEFT THE PARTY."));
	}

	@Test
	void aPlayerTypingTheWordsInChatDoesNotCount() {
		assertFalse(matches("[MVP+] Bob: Alice has left the party."));
		assertFalse(matches("Party > [MVP+] Bob: Alice has left the party."));
		assertFalse(matches("Bob: the party was disbanded"));
		assertFalse(matches("hey has left the party lol"));
	}

	@Test
	void youLeavingYourselfIsNotASomeoneElseLeaving() {
		assertFalse(matches("You left the party."));
	}

	@Test
	void unrelatedLinesDoNotMatch() {
		assertFalse(matches("Davidcovi was killed by Nest Endermite."));
		assertFalse(matches("Davidcovi has joined the party."));
		assertFalse(matches(""));
	}

	@Test
	void aBrokenPatternInTheConfigIsIgnored() {
		assertTrue(PartyLeave.matches("x has left the party.", List.of("([unclosed", "^x has left.*")));
		assertFalse(PartyLeave.matches("x has left the party.", List.of("([unclosed")));
	}

	@Test
	void anEmptyPatternListNeverMatches() {
		assertFalse(PartyLeave.matches("Davidcovi has left the party.", List.of()));
	}
}
