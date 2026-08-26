package com.spoon.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * How a luck figure is worded, which is the number people screenshot.
 */
public class GroupViewTest
{
	@Test
	public void readsAsAPercentage()
	{
		assertEquals("top 40%", GroupView.percent(0.3994));
		assertEquals("top 70%", GroupView.percent(0.6988));
	}

	/**
	 * The best drops are the ones most likely to round to nothing. A one-in-five-hundred stroke of
	 * luck shown as "top 0%" reads as a bug rather than as the best number on the screen.
	 */
	@Test
	public void theAbsurdOnesDoNotRoundToZero()
	{
		assertEquals("top <1%", GroupView.percent(0.002));
		assertEquals("top <1%", GroupView.percent(0.0000001));
	}

	@Test
	public void onePercentIsStillAPercentage()
	{
		assertEquals("top 1%", GroupView.percent(0.01));
	}
}
