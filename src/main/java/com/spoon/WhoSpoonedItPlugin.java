package com.spoon;

import com.google.inject.Provides;
import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import com.spoon.track.ClogWatcher;
import com.spoon.track.DropSender;
import com.spoon.track.GroupStore;
import com.spoon.track.SpoonStore;
import com.spoon.net.SpoonApi;
import com.spoon.ui.CreateGroupPanel;
import com.spoon.ui.ClaimPanel;
import com.spoon.ui.ClaimsPanel;
import com.spoon.ui.GroupView;
import com.spoon.ui.ItemIcons;
import com.spoon.ui.MemberView;
import com.spoon.ui.JoinGroupPanel;
import com.spoon.ui.Medals;
import com.spoon.ui.SpoonPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Who Spooned It?",
	description = "Records how lucky each collection log drop was, and compares it with your group",
	tags = {"collection", "log", "clog", "luck", "drop", "rate", "spoon", "group"}
)
public class WhoSpoonedItPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClogWatcher watcher;

	@Inject
	private SpoonStore spoons;

	@Inject
	private GroupStore groups;

	@Inject
	private SpoonPanel panel;

	@Inject
	private DropSender sender;

	@Inject
	private com.spoon.data.DropRates dropRates;

	@Inject
	private net.runelite.client.game.ItemManager itemManager;

	@Inject
	private ItemIcons icons;

	@Inject
	private SpoonApi api;

	@Inject
	private Medals medals;

	@Inject
	private java.util.concurrent.ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessages;

	@Inject
	private WhoSpoonedItConfig config;

	private NavigationButton navigationButton;

	@Provides
	WhoSpoonedItConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WhoSpoonedItConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Registered by hand rather than being a plugin of its own, so it stops listening the moment
		// this plugin is switched off.
		eventBus.register(watcher);
		watcher.setOnCaptured(this::record);

		spoons.load();
		groups.load();

		// An open leaderboard catches up on its own once drops land, rather than waiting for a press.
		sender.setOnSent(this::onDropsSent);
		sender.start();

		// Wired here rather than inside the panel, so the panel stays a view and does not have to know
		// how a group gets made.
		panel.setActions(this::createGroup, this::joinGroup, this::openGroup);

		navigationButton = NavigationButton.builder()
			.tooltip("Who Spooned It?")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		sender.stop();
		eventBus.unregister(watcher);
		clientToolbar.removeNavigation(navigationButton);
	}

	/**
	 * A drop has been seen. Kept, and said out loud if the player wants it said.
	 */
	private void record(Spoon spoon)
	{
		if (!spoons.add(spoon))
		{
			// Already had it. The log announces a slot the first time it fills, so this means a repeat
			// rather than a second drop, and the first record is the one worth keeping.
			return;
		}

		panel.refresh();

		// Off to every group this account is in. A drop is not shared with one and hidden from another.
		sender.nudge();

		if (config.announceInChat() && spoon.isScored())
		{
			announce(spoon);
		}
	}

	private void announce(Spoon spoon)
	{
		int top = Math.max(1, (int) Math.round(spoon.getShare() * 100));
		String verdict = Luck.isSpooned(spoon.getShare()) ? "Spooned!" : "About time.";

		chatMessages.queue(QueuedMessage.builder()
			.type(net.runelite.api.ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(String.format(
				"%s %s on kill %d — top %d%% of everyone who went for it.",
				verdict, spoon.getItemName(), spoon.getKillCount(), top))
			.build());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Drops are stored per account, so they cannot be read until there is an account to read
			// them for. Logging in on a second character swaps them over rather than merging them.
			spoons.load();
			groups.load();
			panel.refresh();
		}
	}

	/**
	 * Drops have reached a group. Only the open group screen is redrawn, and only by reopening it,
	 * since the leaderboard it shows is the service's rather than anything held here.
	 */
	/**
	 * Brings back anything a group knows about this account that this client never saw.
	 * <p>
	 * An import or a carried claim lands on the service, not here, so without this the group would
	 * credit somebody with a spoon while their own front screen said nothing had ever happened. Two
	 * different questions, one of which looks like a broken plugin.
	 * <p>
	 * Run after an import and after a claim carries, which are the only two ways it can happen.
	 */
	private void catchUpFromGroup(String code)
	{
		String rsn = localPlayerName();
		if (rsn == null)
		{
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.MemberDrops> result =
				api.memberDrops(config.serverUrl(), code, rsn, "recent");

			if (!result.ok())
			{
				return;
			}

			SwingUtilities.invokeLater(() ->
			{
				int taken = 0;

				for (com.spoon.data.Holder held : result.getValue().getDrops())
				{
					com.spoon.data.Spoon spoon = new com.spoon.data.Spoon();
					spoon.setItemName(held.getItemName());
					spoon.setItemId(held.getItemId());
					spoon.setSource(held.getSource() == null ? "" : held.getSource());
					spoon.setKillCount(held.getKillCount() == null ? -1 : held.getKillCount());
					spoon.setDenominator(held.getDenominator() == null ? -1 : held.getDenominator());
					spoon.setShare(held.getShare() == null ? -1 : held.getShare());
					spoon.setObtainedAt(held.getObtainedAt());

					// Marked as claimed whatever it was, because this client did not see it happen.
					spoon.setClaimed(true);

					// Already recorded on the service under its own id, so it must never be sent back.
					if (spoons.take(spoon))
					{
						groups.markSent(code, java.util.Collections.singletonList(spoon.getId()));
						taken++;
					}
				}

				if (taken > 0)
				{
					log.debug("Took {} drops back from {}", taken, code);
					panel.refresh();
				}
			});
		});
	}

	private void onDropsSent()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (openView != null && openCode != null)
			{
				openGroup(openCode);
			}
			else
			{
				panel.refresh();
			}
		});
	}

	private void createGroup()
	{
		openView = null;
		openCode = null;
		openClaims = null;
		panel.show(new CreateGroupPanel(this::doCreate, panel::showList));
	}

	private void joinGroup()
	{
		openView = null;
		openCode = null;
		openClaims = null;
		panel.show(new JoinGroupPanel(this::doJoin, panel::showList));
	}

	private void doCreate(String name)
	{
		String rsn = localPlayerName();
		if (rsn == null)
		{
			warn("Log in first — a group is joined under your own name.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.create(config.serverUrl(), name, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), snapshot.getCreatorToken(), snapshot.getMemberToken());
				openGroup(snapshot.getGroup().getCode());
			});
		});
	}

	private void doJoin(String code)
	{
		String rsn = localPlayerName();
		if (rsn == null)
		{
			warn("Log in first — a group is joined under your own name.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.join(config.serverUrl(), code, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), null, snapshot.getMemberToken());
				openGroup(snapshot.getGroup().getCode());
			});
		});
	}

	private void openGroup(String code)
	{
		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.read(config.serverUrl(), code);

			SwingUtilities.invokeLater(() ->
			{
				if (result.isGone())
				{
					// The service answered and said there is no such group: whoever made it deleted it.
					// Keeping it on the list means a card that cannot be opened and cannot be removed.
					forget(code);
					return;
				}

				if (!result.ok())
				{
					warn(result.getError());
					panel.showList();
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), null, null);

				boolean creator = groups.creatorTokenFor(code) != null;

				GroupView view = new GroupView(
					snapshot.getGroup(),
					snapshot.getLeaderboard(),
					localPlayerName(),
					creator,
					medals::label,
					query -> search(code, query),
					panel::showList,
					() -> openGroup(code),
					() -> leave(code, creator),
					earlierDrops(code),
					() -> shareEarlier(code),
					() -> importFromDiscord(code),
					claimsPanel(code),
					() -> claimADrop(code),
					who -> openMember(code, who, "recent"));

				panel.show(view);
				this.openView = view;
				this.openCode = code;

				// Opening a group is the moment to reconcile: anything it knows about this account that
				// this client never saw belongs on the front screen too. Cheap, because a drop already
				// taken is skipped by name, and it is the only place somebody would notice the two
				// disagreeing.
				catchUpFromGroup(code);
			});
		});
	}

	/** Held so search results can be filled into the group screen that asked for them. */
	private GroupView openView;

	/** Which group that screen is showing, so a send can refresh the right one. */
	private String openCode;

	/** The claims section of the open group, filled in once the service answers. */
	private ClaimsPanel openClaims;

	private void search(String code, String query)
	{
		if (query == null || query.trim().length() < 2)
		{
			if (openView != null)
			{
				openView.showSearchMessage("Type at least two letters of an item's name.");
			}

			return;
		}

		GroupView asked = openView;
		asked.showSearchMessage("Looking...");

		executor.execute(() ->
		{
			SpoonApi.Result<java.util.List<com.spoon.data.Holder>> result =
				api.whoSpoonedIt(config.serverUrl(), code, query.trim());

			SwingUtilities.invokeLater(() ->
			{
				// Only if that same screen is still up. A slow answer arriving after someone has gone
				// back should change nothing.
				if (openView != asked)
				{
					return;
				}

				if (!result.ok())
				{
					asked.showSearchMessage(result.getError());
					return;
				}

				asked.showHolders(query.trim(), result.getValue(), medals::label, icons);
			});
		});
	}

	/**
	 * How many recorded drops predate joining this group, and so have not been shared.
	 * <p>
	 * Counted against everything already sent, so the offer disappears once it has been taken up
	 * rather than sitting there claiming there is more to give.
	 */
	private int earlierDrops(String code)
	{
		com.spoon.track.GroupStore.Membership membership = groups.find(code);
		if (membership == null || membership.sharedFrom == 0)
		{
			return 0;
		}

		int earlier = 0;
		for (com.spoon.data.Spoon spoon : spoons.all())
		{
			if (spoon.getObtainedAt() < membership.sharedFrom
				&& (membership.sent == null || !membership.sent.contains(spoon.getId())))
			{
				earlier++;
			}
		}

		return earlier;
	}

	private void shareEarlier(String code)
	{
		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel,
			"Share every drop already recorded on this account with this group?"
				+ System.lineSeparator() + System.lineSeparator()
				+ "They will appear on its leaderboard and in its searches.",
			"Who Spooned It?",
			javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer != javax.swing.JOptionPane.YES_OPTION)
		{
			return;
		}

		groups.shareEverything(code);

		// Gone from the screen at once. The send follows in a few seconds, and waiting for it would
		// leave the card offering what has just been handed over.
		if (openView != null)
		{
			openView.setEarlierDrops(0);
		}

		sender.nudge();
	}

	/**
	 * One member's drops, on their own.
	 * <p>
	 * The screen is drawn from the same answer that fills it, so the totals at the top and the list
	 * below can never disagree — which they would if the panel were built from the leaderboard and then
	 * filled from a second call.
	 */
	private void openMember(String code, String rsn, String sort)
	{
		openView = null;
		openClaims = null;

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.MemberDrops> result =
				api.memberDrops(config.serverUrl(), code, rsn, sort);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					openGroup(code);
					return;
				}

				SpoonApi.MemberDrops drops = result.getValue();

				MemberView view = new MemberView(
					drops.getRsn(),
					drops.getSpoons(),
					drops.getScored(),
					drops.getAvgShare(),
					drops.getSort(),
					icons,
					chosen -> openMember(code, rsn, chosen),
					() -> openGroup(code));

				view.show(drops.getDrops());
				panel.show(view);
			});
		});
	}

	/**
	 * The claims section, empty until the service answers.
	 * <p>
	 * Loaded after the screen is drawn rather than before it, so a slow answer holds nothing up.
	 */
	private ClaimsPanel claimsPanel(String code)
	{
		ClaimsPanel claims = new ClaimsPanel(
			(claimId, approve) -> vote(code, claimId, approve),
			net.runelite.client.util.LinkBrowser::browse);

		openClaims = claims;
		claims.showMessage("Looking...");
		refreshClaims(code, claims);

		return claims;
	}

	private void refreshClaims(String code, ClaimsPanel into)
	{
		String token = groups.memberTokenFor(code);
		if (token == null)
		{
			into.showMessage("Join this group to see what it is voting on.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<java.util.List<com.spoon.data.Claim>> result =
				api.claims(config.serverUrl(), code, token);

			SwingUtilities.invokeLater(() ->
			{
				// Only if that same section is still on screen. A slow answer arriving after someone has
				// gone back should change nothing.
				if (openClaims != into)
				{
					return;
				}

				if (!result.ok())
				{
					into.showMessage(result.getError());
					return;
				}

				into.show(result.getValue());
			});
		});
	}

	private void claimADrop(String code)
	{
		openView = null;
		openCode = null;
		openClaims = null;
		panel.show(new ClaimPanel(claim -> submitClaim(code, claim), () -> openGroup(code)));
	}

	private void submitClaim(String code, com.spoon.data.Claim claim)
	{
		String token = groups.memberTokenFor(code);
		if (token == null)
		{
			warn("Join this group before claiming anything.");
			return;
		}

		// Looked up here rather than typed, so a claim is scored on the same rate as a drop the plugin
		// watched. Nobody gets to name their own odds.
		if (claim.getSource() != null && !claim.getSource().isEmpty())
		{
			for (com.spoon.data.DropRates.Drop drop : dropRates.dropsFrom(claim.getSource()))
			{
				net.runelite.api.ItemComposition composition =
					itemManager.getItemComposition(drop.itemId);

				if (composition != null && claim.getItemName().equalsIgnoreCase(composition.getName()))
				{
					claim.setItemId(drop.itemId);
					claim.setDenominator(drop.denominator);
					break;
				}
			}
		}

		executor.execute(() ->
		{
			SpoonApi.Result<java.util.List<com.spoon.data.Claim>> result =
				api.claim(config.serverUrl(), code, token, claim);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				warn("Put to the group. They will vote on it.");
				openGroup(code);
			});
		});
	}

	private void vote(String code, String claimId, boolean approve)
	{
		String token = groups.memberTokenFor(code);
		if (token == null)
		{
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<String> result = api.vote(config.serverUrl(), code, token, claimId, approve);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				String settled = result.getValue();
				if (settled == null)
				{
					// Still waiting on other people; only the tally has moved.
					if (openCode != null && openClaims != null)
					{
						refreshClaims(openCode, openClaims);
					}

					return;
				}

				// Settled, so the leaderboard has moved too and the whole screen is stale.
				warn("accepted".equals(settled)
					? "Carried. It is on the board, marked as claimed."
					: "Not carried.");

				if ("accepted".equals(settled))
				{
					catchUpFromGroup(code);
				}

				openGroup(code);
			});
		});
	}

	/**
	 * Brings in the group's history from its Discord channel.
	 * <p>
	 * Asked about first, always. An import that silently discarded a third of a channel is one nobody
	 * would trust afterwards, so what was found is put in front of the creator before anything is
	 * kept — including whose names were not recognised, since a name that has been changed is the
	 * commonest reason for a drop to go missing.
	 */
	private void importFromDiscord(String code)
	{
		String creatorToken = groups.creatorTokenFor(code);
		if (creatorToken == null)
		{
			warn("Only whoever made this group can import its history.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Import> look =
				api.importFromDiscord(config.serverUrl(), code, creatorToken, true);

			SwingUtilities.invokeLater(() ->
			{
				if (!look.ok())
				{
					warn(look.getError());
					return;
				}

				SpoonApi.Import found = look.getValue();

				if (found.isNeedsBot())
				{
					offerInvite(found);
					return;
				}

				if (found.getMatched() == 0)
				{
					warn(describe(found) + System.lineSeparator() + System.lineSeparator()
						+ "Nothing to bring in.");
					return;
				}

				int answer = javax.swing.JOptionPane.showConfirmDialog(
					panel,
					describe(found) + System.lineSeparator() + System.lineSeparator() + "Bring them in?",
					"Who Spooned It?",
					javax.swing.JOptionPane.YES_NO_OPTION);

				if (answer != javax.swing.JOptionPane.YES_OPTION)
				{
					return;
				}

				executor.execute(() ->
				{
					SpoonApi.Result<SpoonApi.Import> done =
						api.importFromDiscord(config.serverUrl(), code, creatorToken, false);

					SwingUtilities.invokeLater(() ->
					{
						if (!done.ok())
						{
							warn(done.getError());
							return;
						}

						warn("Brought in " + done.getValue().getImported() + " drops.");

						// Whatever of that was this account's own belongs on the front screen too.
						catchUpFromGroup(code);
						openGroup(code);
					});
				});
			});
		});
	}

	/**
	 * Offers to open Discord so the bot can be added, rather than telling someone to go and do it.
	 * <p>
	 * Through LinkBrowser, which is the sanctioned way for a plugin to open a web page — and the only
	 * one. Opening a local path is restricted on the plugin hub; a link is not.
	 */
	private void offerInvite(SpoonApi.Import found)
	{
		if (found.getInvite() == null)
		{
			warn("This service has no Discord bot set up.");
			return;
		}

		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel,
			"The bot is not in your Discord server yet." + System.lineSeparator()
				+ System.lineSeparator()
				+ "Open Discord to add it? You will need someone who can manage the server."
				+ System.lineSeparator() + System.lineSeparator()
				+ "Once it is in, type this in the channel your Dink messages go to:"
				+ System.lineSeparator() + "    " + found.getLinkCommand(),
			"Who Spooned It?",
			javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer == javax.swing.JOptionPane.YES_OPTION)
		{
			net.runelite.client.util.LinkBrowser.browse(found.getInvite());
		}
	}

	/** What the import found, in words rather than numbers on their own. */
	private String describe(SpoonApi.Import found)
	{
		StringBuilder text = new StringBuilder();
		text.append("Found ").append(found.getFound()).append(" collection log messages.")
			.append(System.lineSeparator())
			.append(found.getMatched()).append(" belong to people in this group.");

		if (found.getWithoutKillCount() > 0)
		{
			text.append(System.lineSeparator())
				.append(found.getWithoutKillCount())
				.append(" have no kill count, so they count but cannot be scored.");
		}

		if (!found.getUnmatched().isEmpty())
		{
			text.append(System.lineSeparator()).append(System.lineSeparator())
				.append("Not in this group, and ignored:");

			int shown = 0;
			for (java.util.Map.Entry<String, Integer> entry : found.getUnmatched().entrySet())
			{
				if (shown++ == 6)
				{
					text.append(System.lineSeparator()).append("  and others");
					break;
				}

				text.append(System.lineSeparator()).append("  ").append(entry.getKey())
					.append(" (").append(entry.getValue()).append(")");
			}
		}

		return text.toString();
	}

	private void leave(String code, boolean creator)
	{
		String question = creator
			? "Delete this group? It goes for everyone in it, along with every drop recorded in it."
			: "Leave this group? Your drops stay on your own machine.";

		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel, question, "Who Spooned It?", javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer != javax.swing.JOptionPane.YES_OPTION)
		{
			return;
		}

		String creatorToken = groups.creatorTokenFor(code);

		if (creator && creatorToken != null)
		{
			executor.execute(() -> api.delete(config.serverUrl(), code, creatorToken));
		}

		groups.remove(code);
		panel.showList();
	}

	/** Takes a group that no longer exists off this account's list, once the player says so. */
	private void forget(String code)
	{
		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel,
			"That group no longer exists. Remove it from your list?",
			"Who Spooned It?",
			javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer == javax.swing.JOptionPane.YES_OPTION)
		{
			groups.remove(code);
		}

		panel.showList();
	}

	private void warn(String message)
	{
		javax.swing.JOptionPane.showMessageDialog(
			panel, message, "Who Spooned It?", javax.swing.JOptionPane.INFORMATION_MESSAGE);
	}

	/** The name drops are recorded under. Null until logged in. */
	private String localPlayerName()
	{
		return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
	}

	/**
	 * The sidebar icon: a spoon, drawn rather than shipped as a file so there is one less thing for the
	 * plugin hub's packaging to get wrong.
	 */
	private BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(new Color(240, 176, 62));
		graphics.fillOval(5, 1, 6, 7);
		graphics.fillRoundRect(7, 7, 2, 8, 2, 2);

		graphics.dispose();
		return image;
	}
}
