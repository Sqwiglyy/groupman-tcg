package com.groupmantcg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure card draw and winner rules shared by both duel participants. */
final class TopTrumpsRules
{
	private TopTrumpsRules()
	{
	}

	static Match draw(Set<String> collection, CardVisualCatalog catalog, Random random, String challengeId)
	{
		List<CardVisualCatalog.CardVisual> pool = new ArrayList<>();
		for (String cardName : collection)
		{
			CardVisualCatalog.CardVisual card = catalog.find(cardName);
			if (card != null)
			{
				pool.add(card);
			}
		}
		pool.sort(Comparator.comparing(CardVisualCatalog.CardVisual::displayName,
			String.CASE_INSENSITIVE_ORDER));
		if (pool.size() < 2)
		{
			return null;
		}

		CardVisualCatalog.CardVisual challenger = pool.remove(random.nextInt(pool.size()));
		CardVisualCatalog.CardVisual challenged = pool.get(random.nextInt(pool.size()));
		int comparison = Double.compare(challenger.score(), challenged.score());
		boolean tieBreak = comparison == 0;
		int winner = comparison > 0 ? 0 : comparison < 0 ? 1
			: deterministicTieWinner(challengeId, challenger.displayName(), challenged.displayName());
		return new Match(challenger, challenged, winner, tieBreak);
	}

	static int winner(double challengerScore, double challengedScore, String challengeId,
		String challengerCard, String challengedCard)
	{
		int comparison = Double.compare(challengerScore, challengedScore);
		return comparison > 0 ? 0 : comparison < 0 ? 1
			: deterministicTieWinner(challengeId, challengerCard, challengedCard);
	}

	private static int deterministicTieWinner(String challengeId, String challengerCard, String challengedCard)
	{
		String seed = String.valueOf(challengeId) + '\n' + challengerCard + '\n' + challengedCard;
		return Math.floorMod(seed.hashCode(), 2);
	}

	static final class Match
	{
		private final CardVisualCatalog.CardVisual challengerCard;
		private final CardVisualCatalog.CardVisual challengedCard;
		private final int winner;
		private final boolean tieBreak;

		private Match(CardVisualCatalog.CardVisual challengerCard,
			CardVisualCatalog.CardVisual challengedCard, int winner, boolean tieBreak)
		{
			this.challengerCard = challengerCard;
			this.challengedCard = challengedCard;
			this.winner = winner;
			this.tieBreak = tieBreak;
		}

		CardVisualCatalog.CardVisual challengerCard() { return challengerCard; }
		CardVisualCatalog.CardVisual challengedCard() { return challengedCard; }
		int winner() { return winner; }
		boolean tieBreak() { return tieBreak; }
	}
}

