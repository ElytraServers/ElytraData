package cn.elytra.data;

import com.google.common.collect.Lists;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.commons.lang.ArrayUtils;
import org.bson.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ElytraDataPlugin extends JavaPlugin {

	private static ElytraDataPlugin INST;

	public static final int[] ALLOWED_CONFIGURATION_VERSION = new int[]{0, 1};

	/**
	 * Get the instance of ElytraData plugin.
	 *
	 * @return the instance
	 */
	@NotNull
	public static ElytraDataPlugin getInstance() {
		if(INST == null) {
			throw new IllegalStateException("Access ElytraData too early");
		}
		return INST;
	}

	protected final Logger logger;

	@Nullable
	protected Configuration config;

	@Nullable
	protected MongoClient client;

	@Nullable
	protected MongoDatabase testDatabase;

	protected int clientStatus = CLIENT_STATUS_CONNECT;

	/**
	 * Disabled in Configuration
	 */
	public static final int CLIENT_STATUS_DISABLE = -1;

	/**
	 * Invoked the Connection and no Exception
	 */
	public static final int CLIENT_STATUS_CONNECT = 0;

	/**
	 * Invoked the Connection and passed the Validation
	 */
	public static final int CLIENT_STATUS_PERFECT = 1;

	/**
	 * Invoked the Connection and failed to pass the Validation
	 */
	public static final int CLIENT_STATUS_ERROR = 2;

	public ElytraDataPlugin() {
		INST = this;

		this.logger = getLogger();

		loadDatabase();
	}

	protected void loadDatabase() {
		// Do the Chores of Reload
		if(this.client != null) {
			this.client.close();
		}
		this.client = null;
		this.testDatabase = null;

		logger.fine("Loading configuration");
		this.saveDefaultConfig();
		this.reloadConfig();
		this.config = getConfig();

		int configSpec = this.config.getInt("configSpec");
		logger.fine("Reading configuration at Spec " + configSpec);
		if(!ArrayUtils.contains(ALLOWED_CONFIGURATION_VERSION, configSpec)) {
			logger.warning("Invalid config. The ConfigSpec is " + configSpec + " not included in the AllowingList(" + Arrays.toString(ALLOWED_CONFIGURATION_VERSION) + ")");
			logger.warning(ChatColor.BLUE + "Skipping!");
			var ignored = new File(this.getDataFolder(), "config.yml").renameTo(new File(this.getDataFolder(), "config.error.yml"));
			this.saveDefaultConfig();
			return;
		}

		try {
			// Prepare the Database
			var enable = config.getBoolean("mongo.enable");
			if(enable) {
				logger.info("Loading Mongo!");
				var dbUri = config.getString("mongo.database");
				this.client = MongoClients.create(Objects.requireNonNull(dbUri, "Database Url"));
				this.testDatabase = client.getDatabase("ElytraData_Testing");
				clientStatus = CLIENT_STATUS_CONNECT;
			} else {
				logger.info(ChatColor.BLUE + "Skipping Mongo!");
				clientStatus = CLIENT_STATUS_DISABLE;
			}
		} catch(Exception ex) {
			logger.log(Level.SEVERE, "Error occurred while loading Mongo.");
			clientStatus = CLIENT_STATUS_ERROR;
		}
	}

	@Override
	public void onEnable() {
		var edataCmd = new EdataCommand();
		var testCommand = getCommand("edata");
		if(testCommand != null) {
			testCommand.setExecutor(edataCmd);
			testCommand.setTabCompleter(edataCmd);
		} else {
			logger.warning("Unable to register command edata");
		}

		// Invoke the Validator async
		Bukkit.getScheduler().runTaskAsynchronously(this, this::checkDatabase);
	}

	@Override
	public void onDisable() {
		logger.info("Disabling ElytraData");
		if(this.client != null) {
			this.client.close();
			logger.info(ChatColor.BLUE + "Mongo closed!");
		}
	}

	protected void checkDatabase() {
		if(this.client != null) {
			try {
				var doc = getTestDatabase().getCollection("Validate_Document", BsonDocument.class);

				var s = getServer();
				var info = new BsonDocument();
				info.put("Startup-Time", new BsonTimestamp(System.currentTimeMillis()));
				info.put("Server-Name", new BsonString(s.getName()));
				info.put("Server-Version", new BsonString(s.getVersion()));
				info.put("Bukkit-Version", new BsonString(s.getBukkitVersion()));
				info.put("Server-Whitelisted", new BsonBoolean(s.hasWhitelist()));
				info.put("Server-Whitelist", new BsonArray(s.getWhitelistedPlayers().stream().map(OfflinePlayer::getName).filter(Objects::nonNull).map(BsonString::new).collect(Collectors.toList())));

				doc.insertOne(info);

				clientStatus = CLIENT_STATUS_PERFECT;
			} catch(Exception ex) {
				clientStatus = CLIENT_STATUS_ERROR;
				logger.log(Level.WARNING, ChatColor.BLUE + "Mongo Validation Failed!", ex);
			}
		}
	}

	@NotNull
	public MongoClient getClient() {
		if(this.client == null) throw new IllegalStateException("MongoClient is not available");
		return this.client;
	}

	@NotNull
	public MongoDatabase getDatabase(String databaseName) {
		return getClient().getDatabase(databaseName);
	}

	@NotNull
	public MongoDatabase getDatabase(Plugin plugin) {
		return getDatabase("ElytraData_"+plugin.getName());
	}

	@NotNull
	public MongoDatabase getTestDatabase() {
		if(this.testDatabase == null) throw new IllegalStateException("Test Database is not available");
		return this.testDatabase;
	}

	public class EdataCommand implements CommandExecutor, TabCompleter {

		private static final String STATUS_TEMPLATE = """
				===> Elytra Database <===
				Mongo Status: %s
				Mongo Client: %s
				""";

		@Override
		public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
			if(args.length > 0) {
				if(args[0].equals("reload")) {
					loadDatabase();
					sender.sendMessage("Reloaded");
				} else {
					sender.sendMessage("Invalid Arguments.");
				}
			} else {
				Arrays.stream(
						String.format(STATUS_TEMPLATE,
								getStatusString(ElytraDataPlugin.this.clientStatus),
								ElytraDataPlugin.this.client != null ? ElytraDataPlugin.this.client : "/"
						).split("\n")
				).forEach(sender::sendMessage);
			}
			return true;
		}

		@Nullable
		@Override
		public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
			return Lists.newArrayList("reload");
		}

		private static String getStatusString(int status) {
			return switch(status) {
				case CLIENT_STATUS_DISABLE -> "Disabling";
				case CLIENT_STATUS_CONNECT -> "Validating";
				case CLIENT_STATUS_PERFECT -> "Working";
				case CLIENT_STATUS_ERROR -> "Error";
				default -> "Unknown";
			};
		}
	}
}
