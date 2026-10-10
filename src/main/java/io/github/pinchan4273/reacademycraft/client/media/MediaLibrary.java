package io.github.pinchan4273.reacademycraft.client.media;

import io.github.pinchan4273.reacademycraft.media.MediaAcquired;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * クライアント側の原作MediaManager: modに同梱のメディア、次にプレイヤーが.minecraft/acmedia/sourceに置いた.oggファイル。
 * それぞれacmedia/coverの同名の.pngをカバーとし、無ければ原作のicon_nomediaを使う。初めて一覧を読むときに
 * フォルダを作り、原作のREADME.txtをacmediaに置く。
 *
 * 原作は音声ライブラリがファイルからストリーム再生できるよう、自身の曲をacmediaへコピーする。ここの音声エンジンは
 * modのリソースからストリーム再生するので、何もコピーしない。音を読めないメディアは一覧から外す（原作もコピーや
 * 長さの測定ができないものは登録しない）。そのようなメディア（missing）の未導入の案内は移植版独自のもの。
 */
public final class MediaLibrary {
    public static final ResourceLocation NO_COVER = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/icon_nomedia.png");
    private static List<Media> all;
    private MediaLibrary() { }

    public static Path root() { return Minecraft.getInstance().gameDirectory.toPath().resolve("acmedia"); }
    /** すべてのメディア。modのものが先で、原作の登録順。 */
    public static List<Media> all() {
        if (all == null) all = load();
        return all;
    }
    /** 原作MediaGuiの一覧: このプレイヤーが持つすべてのメディア（外部のものは全部）。 */
    public static List<Media> installed(Player player) {
        var acquired = MediaAcquired.installed(player);
        return all().stream().filter(media -> media.external() || acquired.contains(media.id())).toList();
    }
    /**
     * このプレイヤーが取得済みだが、音声がここに無いmod自身のメディア。このビルドは原作の商用楽曲を一切同梱しないので、
     * プレイヤーが（個人用リソースパックで）追加するまでは未導入として、追加方法の案内と共に表示する。
     */
    public static List<String> missing(Player player) {
        var here = all().stream().filter(m -> !m.external()).map(Media::id).toList();
        return MediaAcquired.installed(player).stream().filter(id -> !here.contains(id)).toList();
    }
    /** このidのmod自身のメディアの音声がここにあるか。 */
    public static boolean hasSound(String id) {
        return all().stream().anyMatch(m -> !m.external() && m.id().equals(id));
    }
    /** メディアの追加方法を書いたacmedia内のファイル。プレイヤーへの案内が指す先。 */
    public static final String HOW_TO = "HOW_TO_ADD_MEDIA.txt";
    /** リソースパックはmod自身の曲とカバーを持てるので、リソースの読み込み後に一覧を読み直す。 */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "academy", value = net.minecraftforge.api.distmarker.Dist.CLIENT,
            bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> reload());
        }
    }
    /** フォルダを読み直す。原作はゲーム開始時に一度だけ読む。 */
    public static void reload() {
        if (all != null) for (var media : all)
            if (media.external() && !media.cover().equals(NO_COVER)) Minecraft.getInstance().getTextureManager().release(media.cover());
        all = null;
    }

    private static List<Media> load() {
        var out = new ArrayList<Media>();
        var log = LogUtils.getLogger();
        var resources = Minecraft.getInstance().getResourceManager();
        for (var id : MediaAcquired.INTERNAL) {
            var source = ResourceLocation.fromNamespaceAndPath("academy", "sounds/media/" + id + ".ogg");
            var resource = resources.getResource(source);
            // 原作の3曲は商用楽曲のため同梱しない。個人用リソースパックで追加できる。
            if (resource.isEmpty()) { log.info("Media {} has no sound here (not shipped; see acmedia/{})", id, HOW_TO); continue; }
            try (var in = resource.get().open()) {
                float length = length(in.readAllBytes());
                if (length < 0) { log.error("Can't read media file {}.", id); continue; }
                out.add(new Media(false, id, source, null, ResourceLocation.fromNamespaceAndPath("academy", "media/" + id),
                        cover(id), length));
            } catch (IOException e) {
                log.error("Can't read media file {}.", id);
            }
        }
        var root = root();
        Path sources = root.resolve("source"), covers = root.resolve("cover");
        try {
            Files.createDirectories(sources); Files.createDirectories(covers);
            try (var readme = resources.getResource(ResourceLocation.fromNamespaceAndPath("academy", "media/readme_template.txt"))
                    .orElseThrow(() -> new IOException("no readme")).open()) {
                Files.copy(readme, root.resolve("README.txt"), StandardCopyOption.REPLACE_EXISTING);
            }
            try (var howTo = resources.getResource(ResourceLocation.fromNamespaceAndPath("academy", "media/how_to_add_media.txt"))
                    .orElseThrow(() -> new IOException("no how-to")).open()) {
                Files.copy(howTo, root.resolve(HOW_TO), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Can't copy media readme file.");
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(sources)) {
            files = list.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".ogg")).sorted().toList();
        } catch (IOException e) {
            files = List.of();
        }
        int index = 0;
        for (var file : files) {
            var name = file.getFileName().toString();
            // 原作: idはファイル名の最初のドットまで。
            var id = name.substring(0, name.indexOf('.'));
            if (id.isEmpty() || out.stream().anyMatch(media -> media.id().equals(id))) continue;
            float length;
            try { length = length(Files.readAllBytes(file)); } catch (IOException e) { length = -1; }
            if (length < 0) continue;
            String key = "media/external/" + index++ + "_" + id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            out.add(new Media(true, id, null, file, ResourceLocation.fromNamespaceAndPath("academy", key),
                    externalCover(covers.resolve(id + ".png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/" + key + ".png")), length));
        }
        return List.copyOf(out);
    }
    /** 原作自身のカバーはacademy:media/coverにある。無ければ原作のicon_nomedia。 */
    private static ResourceLocation cover(String id) {
        var cover = ResourceLocation.fromNamespaceAndPath("academy", "textures/media/cover/" + id + ".png");
        return Minecraft.getInstance().getResourceManager().getResource(cover).isPresent() ? cover : NO_COVER;
    }
    private static ResourceLocation externalCover(Path file, ResourceLocation at) {
        if (!Files.isRegularFile(file)) return NO_COVER;
        try (InputStream in = Files.newInputStream(file)) {
            Minecraft.getInstance().getTextureManager().register(at, new DynamicTexture(NativeImage.read(in)));
            return at;
        } catch (IOException e) {
            return NO_COVER;
        }
    }
    /**
     * 原作は各ファイルの長さをJOrbisのtime_totalで測る。ここではゲームが音声のデコードに使うstb_vorbisに問い合わせる。
     * 読めないファイルなら0未満。
     */
    public static float length(byte[] data) {
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            buffer.put(data).flip();
            long handle = STBVorbis.stb_vorbis_open_memory(buffer, stack.mallocInt(1), null);
            if (handle == 0) return -1;
            try { return STBVorbis.stb_vorbis_stream_length_in_seconds(handle); }
            finally { STBVorbis.stb_vorbis_close(handle); }
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
