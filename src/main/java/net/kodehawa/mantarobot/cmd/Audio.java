package net.kodehawa.mantarobot.cmd;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.managers.AudioManager;
import net.kodehawa.mantarobot.audio.MusicManager;
import net.kodehawa.mantarobot.cmd.guild.Parameters;
import net.kodehawa.mantarobot.core.Mantaro;
import net.kodehawa.mantarobot.listeners.generic.GenericListener;
import net.kodehawa.mantarobot.log.Log;
import net.kodehawa.mantarobot.log.Type;
import net.kodehawa.mantarobot.module.Callback;
import net.kodehawa.mantarobot.module.Category;
import net.kodehawa.mantarobot.module.CommandType;
import net.kodehawa.mantarobot.module.Module;
import net.kodehawa.mantarobot.util.*;

import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Audio extends Module {

    private final AudioPlayerManager playerManager;
    private final Map<Long, MusicManager> musicManagers;
    private Member _member;
    private MessageReceivedEvent _eventTemp;
            private GenericListener listener1;
    private Timer timer = new Timer();

    public Audio(){
        super.setCategory(Category.AUDIO);
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
        this.registerCommands();
    }

    @Override
    public void registerCommands(){
        super.register("play", "Plays a song in the music voice channel.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                _eventTemp = event;
                _member = event.getMember();
                try {
                    new URL(content);
                }
                catch(Exception e) {
                    content = "ytsearch: " + content;
                }

                loadAndPlay(event, event.getGuild(), event.getTextChannel(), content);
            }

            @Override
            public String help() {
                return "Plays a song in the music voice channel.\n"
                        + "Usage:\n"
                        + "~>play [youtubesongurl] (Can be a song, a playlist or a search)";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("skip", "Stops the track and continues to the next one, if there is one.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                skipTrack(event.getTextChannel(), event);
            }

            @Override
            public String help() {
                return "Stops the track and continues to the next one, if there is one.\n"
                        + "Usage:\n"
                        + "~>skip";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("shuffle", "Shuffles the current playlist", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                shuffle(musicManager);
                event.getChannel().sendMessage(":mega: Randomized current queue order.").queue();
            }

            @Override
            public String help() {
                return null;
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("stop", "Clears queue and leaves the voice channel.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                clearQueue(musicManager, event, false);
                closeConnection(musicManager, event.getGuild().getAudioManager(), event.getTextChannel());
            }

            @Override
            public String help() {
                return "Clears the queue and leaves the voice channel.\n"
                        + "Usage:\n"
                        + "~>stop";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("queue", "Returns the current track list playing on the server.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                if(content.isEmpty()){
                    event.getChannel().sendMessage(embedQueueList(event.getGuild(), musicManager)).queue();
                } else if(content.startsWith("clear")){
                    clearQueue(musicManager, event, true);
                }
            }

            @Override
            public String help() {
                return "Returns the current queue playing on the server or clears it.\n"
                        + "Usage:\n"
                        + "~>queue"
                        + "~>queue clear";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("removetrack", "Removes the specified track from the queue.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                int n = 0;
                for(AudioTrack audioTrack : musicManager.getScheduler().getQueue()){
                    if(n == Integer.parseInt(content) - 1){
                        event.getChannel().sendMessage("Removed track: " + audioTrack.getInfo().title).queue();
                        musicManager.getScheduler().getQueue().remove(audioTrack);
                        break;
                    }
                    n++;
                }
            }

            @Override
            public String help() {
                return "Removes the specified track from the queue.\n"
                        + "Usage:\n"
                        + "~>removetrack [tracknumber] (as specified on the ~>queue command)";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER ;
            }
        });

        super.register("pause", "Pauses the player.", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                try{
                    musicManager.getScheduler().getPlayer().setPaused(Boolean.parseBoolean(content));
                } catch (Exception e){
                    event.getChannel().sendMessage(":heavy_multiplication_x " + "Error -> Not a boolean value");
                }
            }

            @Override
            public String help() {
                return "Pauses or unpauses the current track.\n"
                        + "Usage:\n"
                        + "~>pause true/false (pause/unpause)";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });

        super.register("np", "What's playing now?", new Callback() {
            @Override
            public void onCommand(String[] args, String content, MessageReceivedEvent event) {
                MusicManager musicManager = musicManagers.get(Long.parseLong(event.getGuild().getId()));
                event.getChannel().sendMessage(":mega: Now playing ->``" + musicManager.getScheduler().getPlayer().getPlayingTrack().getInfo().title
                        + " (" + net.kodehawa.mantarobot.util.Utils.instance().getDurationMinutes(musicManager.getScheduler().getPlayer().getPlayingTrack().getInfo().length) + ")``").queue();
            }

            @Override
            public String help() {
                return "Returns what track is playing now.";
            }

            @Override
            public CommandType commandType() {
                return CommandType.USER;
            }
        });
    }

    private synchronized MusicManager getGuildAudioPlayer(MessageReceivedEvent event, Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        MusicManager musicManager = musicManagers.get(guildId);
        if (musicManager == null) {
            musicManager = new MusicManager(playerManager, event);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(final MessageReceivedEvent event, final Guild guild, final TextChannel channel, final String trackUrl) {
        MusicManager musicManager = getGuildAudioPlayer(event, channel.getGuild());
        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                loadTrack(guild, channel, musicManager, track, false);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                int i = 0;
                if(!playlist.isSearchResult()){
                    for(AudioTrack audioTrack : playlist.getTracks()){
                        if(i <= 60){
                            loadTrack(guild, channel, musicManager, audioTrack, true);
                        } else {
                            break;
                        }
                        i++;
                    }
                    long templength = 0;
                    for(AudioTrack temp : playlist.getTracks()){
                        templength = templength
                                + temp.getInfo().length;
                    }
                    channel.sendMessage("Added **" + playlist.getTracks().size()
                            + " songs** to queue on playlist: **"
                            + playlist.getName() + "**" + " *("
                            + net.kodehawa.mantarobot.util.Utils.instance().getDurationMinutes(templength) + ")*"
                    ).queue();
                } else {
                    String[] args = {"1", "2", "3", "4"};
                    List<String> content = new ArrayList<>();
                    int i1 = 0;
                    for(AudioTrack at : playlist.getTracks()){
                        if(i1 <= 3){
                            content.add(at.getInfo().title + " **(" + net.kodehawa.mantarobot.util.Utils.instance().getDurationMinutes(at.getInfo().length) + ")**");
                        }
                        i1++;
                    }

                    Function<GenericListener, String> builderFunction = (GenericListener listener) -> {
                        int n = 0;
                        StringBuilder b = new StringBuilder();
                        for(Object s : listener._list) {
                            n++;
                            //dot append
                            b.append("[")
                                    .append(n)
                                    .append("] ")
                                    .append(s)
                                    .append("\n");
                        }
                        EmbedBuilder embedBuilder = new EmbedBuilder();
                        embedBuilder.setColor(Color.CYAN);
                        embedBuilder.setTitle("Song selection. Type the song number to continue.");
                        embedBuilder.setDescription(b.toString());
                        embedBuilder.setFooter("This timeouts in 10 seconds.", null);
                        listener._evt.getChannel().sendMessage(
                                embedBuilder.build()).queue();
                        return "Return is invisible, but I love it.";
                    };

                    listener1 = new GenericListener(builderFunction, playlist, guild, musicManager, content, _eventTemp.getAuthor().getId(),
                            args, _eventTemp, (GenericListener listener) -> {
                        if(listener._gEventTmp.getAuthor().getId().equals(listener._userId)) {
                            for (String s : listener._args) {
                                if (listener._gEventTmp.getMessage().getContent().equals(s)) {
                                    loadTrack(listener._guild, listener._gEventTmp.getChannel(), listener._musicManager,
                                            listener._audioPlaylist.getTracks().get(Integer.parseInt(s) - 1), false);
                                    Mantaro.instance().getSelf().removeEventListener(listener1);
                                    timer.cancel();
                                    break;
                                }
                            }
                        }
                        return "Guess this didn't have to return it.";
                    });

                    Mantaro.instance().getSelf().addEventListener(listener1);
                    TimerTask ts = new TimerTask() {
                        @Override
                        public void run() {
                            Mantaro.instance().getSelf().removeEventListener(listener1);
                            channel.sendMessage(":heavy_multiplication_x: Timeout: No reply in 10 seconds").queue();
                        }
                    };

                    timer.schedule(ts, 10000);
                }
            }

            @Override
            public void noMatches() {
                channel.sendMessage(":heavy_multiplication_x: Nothing found on " + trackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if(!exception.severity.equals(FriendlyException.Severity.FAULT)){
                    Log.instance().print("Couldn't play music", this.getClass(), Type.WARNING, exception);
                    channel.sendMessage(":heavy_multiplication_x: Couldn't play music: " + exception.getMessage() + " SEVERITY: " + exception.severity).queue();
                } else {
                    exception.printStackTrace();
                }
            }
        });
    }

    private void play(Guild guild, MusicManager musicManager, AudioTrack track, Member member) {
        connectToUserVoiceChannel(guild.getAudioManager(), member);
        musicManager.getScheduler().queue(track);
    }

    private void play(String cid, Guild guild, MusicManager musicManager, AudioTrack track) {
        connectToNamedVoiceChannel(cid, guild.getAudioManager());
        musicManager.getScheduler().queue(track);
    }

    private void shuffle(MusicManager musicManager){
        java.util.List<AudioTrack> temp = new ArrayList<>();
        BlockingQueue<AudioTrack> bq = musicManager.getScheduler().getQueue();
        if(!bq.isEmpty()){
            bq.drainTo(temp);
        }
        bq.clear();

        java.util.Random rand = new java.util.Random();
        Collections.shuffle(temp, new java.util.Random(rand.nextInt(18975545)));

        for(AudioTrack track : temp){
            bq.add(track);
        }

        temp.clear();
    }

    private void skipTrack(TextChannel channel, MessageReceivedEvent event) {
        MusicManager musicManager = getGuildAudioPlayer(event, channel.getGuild());
        if(nextTrackAvailable(musicManager)){
            musicManager.getScheduler().nextTrack();
            channel.sendMessage(":mega: Skipped to next track -> **" + musicManager.getScheduler().getPlayer().getPlayingTrack().getInfo().title + "**").queue();
        } else {
            channel.sendMessage("No tracks next. Disconnecting...").queue();
            closeConnection(musicManager, event.getGuild().getAudioManager(), channel);
        }
    }

    private static void connectToUserVoiceChannel(AudioManager audioManager, Member member) {
        if(member.getVoiceState().getChannel() != null)
        {
            if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
                audioManager.openAudioConnection(member.getVoiceState().getChannel());
            }
        }
    }

    private static void connectToNamedVoiceChannel(String voiceId, AudioManager audioManager){
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                if(voiceChannel.getId().equals(voiceId)){
                    audioManager.openAudioConnection(voiceChannel);
                    break;
                }
            }
        }
    }

    private void closeConnection(MusicManager musicManager, AudioManager audioManager, TextChannel channel) {
        musicManager.getScheduler().getQueue().clear();
        audioManager.closeAudioConnection();
        channel.sendMessage(":mega: Closed audio connection.").queue();
    }

    private boolean nextTrackAvailable(MusicManager musicManager){
        return musicManager.getScheduler().getQueueSize() > 0;
    }

    private MessageEmbed embedQueueList(Guild guild, MusicManager musicManager) {
        String toSend = musicManager.getScheduler().getQueueList();
        String[] lines = toSend.split("\r\n|\r|\n");
        List<String> lines2 = new ArrayList<>(Arrays.asList(lines));
        StringBuilder stringBuilder = new StringBuilder();
        int temp = 0;
        for(int i = 0; i < lines2.size(); i++){
            temp++;
            if(i <= 14){
                stringBuilder.append
                        (lines2.get(i))
                        .append("\n");
            }
            else {
                lines2.remove(i);
            }
        }

        if(temp > 15){
            stringBuilder.append("\nShowing only first **15** results.");
        }

        long templength = 0;
        for(AudioTrack temp1 : musicManager.getScheduler().getQueue()){
            templength = templength
                    + temp1.getInfo().length;
        }

        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor("Queue for server " + guild.getName(), null, guild.getIconUrl());
        builder.setColor(Color.CYAN);
        if(!toSend.isEmpty()){
            builder.setDescription(stringBuilder.toString());
            builder.addField("Queue runtime", net.kodehawa.mantarobot.util.Utils.instance().getDurationMinutes(templength), true);
            builder.addField("Total queue size", String.valueOf(musicManager.getScheduler().getQueue().size()), true);
        } else {
            builder.setDescription("Nothing here, just dust.");
        }

        return builder.build();
    }

    private void loadTrack(Guild guild, TextChannel channel, MusicManager musicManager, AudioTrack track, boolean isPlaylist){
        try{
            if(track.getDuration() > 600000){
                channel.sendMessage(
                        ":heavy_multiplication_x:"
                                + " Track added is longer than 10 minutes (>600000ms). Cannot add "
                                + track.getInfo().title
                                + " (Track length: " + getDurationMinutes(track) + ")"
                ).queue();
                return;
            }

            if(Parameters.getMusicVChannelForServer(guild.getId()).isEmpty()){
                play(channel.getGuild(), musicManager, track, _member);

                if(!isPlaylist)
                    channel.sendMessage(
                            ":mega: Added to queue -> **" + track.getInfo().title + "**"
                            + " **!(" + getDurationMinutes(track) + ")**"
                    ).queue();
            } else {
                play(Parameters.getMusicVChannelForServer(
                        guild.getId()), channel.getGuild(), musicManager, track);

                if(!isPlaylist)
                    channel.sendMessage(
                            ":mega: Added to queue -> **" + track.getInfo().title + "**"
                                    + " **(" + getDurationMinutes(track) + ")**"
                    ).queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearQueue(MusicManager musicManager, MessageReceivedEvent event, boolean askForSkip){
        int TEMP_QUEUE_LENGHT = musicManager.getScheduler().getQueue().size();
        for(AudioTrack audioTrack : musicManager.getScheduler().getQueue()){
            musicManager.getScheduler().getQueue().remove(audioTrack);
        }
        event.getChannel().sendMessage("Removed **" + TEMP_QUEUE_LENGHT + " songs** from queue.").queue();
        if(askForSkip)
            skipTrack(event.getTextChannel(), event);
    }

    private String getDurationMinutes(AudioTrack track){
        long TRACK_LENGHT = track.getInfo().length;
        return String.format("%d:%02d minutes",
                TimeUnit.MILLISECONDS.toMinutes(TRACK_LENGHT),
                TimeUnit.MILLISECONDS.toSeconds(TRACK_LENGHT) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(TRACK_LENGHT))
        );
    }
}