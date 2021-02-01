package us.mcstats.serverstats.pinger;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.mcstats.serverstats.database.entities.Server;
import us.mcstats.serverstats.database.entities.Timeseries;
import us.mcstats.serverstats.database.repository.ServerRepository;
import us.mcstats.serverstats.database.repository.TimeseriesRepository;
import us.mcstats.serverstats.ping.MCPing;
import us.mcstats.serverstats.ping.MCPingResponse;
import java.sql.Timestamp;
import java.util.Random;

public class PingerThread extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(PingerThread.class);

    private final ServerRepository serverRepository;
    private final TimeseriesRepository repository;
    private final Server server;

    public PingerThread(Server server, TimeseriesRepository timeseriesRepository, ServerRepository serverRepository) {
        LOGGER.info("Thread started for server " + server.getName() + " - " + server.getAddress());
        this.server = server;
        this.repository = timeseriesRepository;
        this.serverRepository = serverRepository;
    }

    @SneakyThrows
    public void run() {
        int rand = new Random().nextInt(60) + 1;
        LOGGER.info("Sleeping initially for " + rand + " seconds");
        Thread.sleep(rand * 1000);

        boolean updatedMetaData = false;

        while (true) {
            try {
                MCPingResponse response = MCPing.getPing(server.getAddress());
                Timeseries timeseries = new Timeseries();
                Timeseries.CompositeKey key = new Timeseries.CompositeKey();
                key.setName(server.getName());
                key.setTimestamp(new Timestamp(System.currentTimeMillis()));
                timeseries.setId(key);
                timeseries.setPlayersOnline(response.getPlayers().getOnline());

                LOGGER.info("Pinged server " + server.getName() + " (players online: " + response.getPlayers().getOnline() + ") saving timeseries: " + timeseries.toString());


                if (!updatedMetaData) {
                    LOGGER.info("Updating meta data for server '" + server.getName() + "'");
                    server.setImage(response.getFavicon());
                    server.setDescription(response.getDescription().getStrippedText());
                    updatedMetaData = true;
                }

                server.setPlayers(response.getPlayers().getOnline());
                server.setMaxPlayers(response.getPlayers().getMax());

                serverRepository.save(server);
                repository.save(timeseries);
            } catch (Exception e) {
                LOGGER.info("Failed to ping " + server.getAddress() + " - " + e.getMessage());
            } finally {
                Thread.sleep(60 * 1000);
            }
        }
    }
}
