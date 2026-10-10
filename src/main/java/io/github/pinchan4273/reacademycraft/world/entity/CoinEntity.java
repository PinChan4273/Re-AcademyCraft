package io.github.pinchan4273.reacademycraft.world.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.AcademySounds;
import io.github.pinchan4273.reacademycraft.world.CoinLedger;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 投げたコイン。飛び方は原作EntityCoinThrowing（KSkun）のもので、判定はサーバーが持つ。
 * アイテムの保管はCoinLedgerだけが持つので、読み込まれていない見た目がそれを複製することはできない。
 */
public final class CoinEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(CoinEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> PROGRESS=SynchedEntityData.defineId(CoinEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ATTEMPTED=SynchedEntityData.defineId(CoinEntity.class,EntityDataSerializers.BOOLEAN);
    private double initialHeight,maximumHeight;
    private int age;
    private boolean resumeAsReturn;
    public CoinEntity(EntityType<? extends CoinEntity> type,Level level){super(type,level);setNoGravity(true);setInvulnerable(true);}
    public static Optional<CoinEntity> toss(ServerPlayer player,InteractionHand hand){
        var stack=player.getItemInHand(hand);var ledger=CoinLedger.get(player.serverLevel());
        if(!stack.is(AcademyContent.COIN.get())||stack.isEmpty()||!player.isAlive()||player.isSpectator())return Optional.empty();
        var before=stack.copy();boolean debited=!player.getAbilities().instabuild;
        var entity=new CoinEntity(AcademyContent.COIN_ENTITY.get(),player.level());
        entity.entityData.set(OWNER,Optional.of(player.getUUID()));entity.setPos(player.position());
        entity.initialHeight=entity.maximumHeight=player.getY();double inherited=player.getDeltaMovement().y;
        entity.setDeltaMovement(0,.92+(Double.isFinite(inherited)?inherited:0),0);
        if(!ledger.reserve(player,before,entity.getUUID()))return Optional.empty();
        boolean finalized=false;
        try{
            if(!player.serverLevel().addFreshEntity(entity)||entity.isRemoved()||!player.isAlive()||player.isSpectator()
                    ||!ItemStack.matches(player.getItemInHand(hand),before)||player.level()!=entity.level())return Optional.empty();
            if(!ledger.commit(player.getUUID(),entity.getUUID()))return Optional.empty();
            if(debited)player.getItemInHand(hand).shrink(1);
            finalized=true;player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
            // 保管の確定に成功した後の原作ItemCoinの音。投げたクライアントも含める。
            player.level().playSound(null,player.getX(),player.getY(),player.getZ(),AcademySounds.ENTITY_FLIPCOIN.get(),SoundSource.PLAYERS,.5f,1f);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new io.github.pinchan4273.reacademycraft.event.AcademyEvent.CoinThrow(player,entity));
            return Optional.of(entity);
        }finally{if(!finalized){ledger.cancelReservation(player.getUUID(),entity.getUUID());entity.discard();}}
    }
    /** エンティティのイベント: コインが投げた者のところへ落ちて戻った。 */
    public static final byte LANDED=60;
    @Override public void handleEntityEvent(byte id){
        if(id==LANDED)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                ()->()->io.github.pinchan4273.reacademycraft.client.CoinHeadsOrTails.landed(this));
        else super.handleEntityEvent(id);
    }
    public Optional<UUID> owner(){return entityData.get(OWNER);}
    public float progress(){return entityData.get(PROGRESS);}
    public boolean railgunAttempted(){return entityData.get(ATTEMPTED);}
    public boolean liveForRailgun(ServerPlayer player){
        return !level().isClientSide&&!isRemoved()&&!resumeAsReturn&&player.isAlive()&&!player.isSpectator()
                &&player.level()==level()&&owner().filter(player.getUUID()::equals).isPresent()
                &&CoinLedger.get(player.serverLevel()).isActive(player.getUUID(),getUUID());
    }
    /** 原作のQTEは、早すぎる外れも含め、1回の投擲につき1回のキー押下の判定を許す。 */
    public boolean beginRailgunAttempt(ServerPlayer player){
        if(railgunAttempted()||!liveForRailgun(player))return false;entityData.set(ATTEMPTED,true);return true;
    }
    /** 見た目を捨てる前に保管を消費するので、二度と返却できない。 */
    public boolean consumeForRailgun(ServerPlayer player){
        if(!railgunAttempted()||!liveForRailgun(player)||progress()<=.7f)return false;
        if(!CoinLedger.get(player.serverLevel()).consume(player.getUUID(),getUUID(),player.serverLevel().getServer().overworld().getGameTime()))return false;
        discard();return true;
    }
    @Override public ItemStack getItem(){return new ItemStack(AcademyContent.COIN.get());}
    @Override protected void defineSynchedData(){entityData.define(OWNER,Optional.empty());entityData.define(PROGRESS,0f);entityData.define(ATTEMPTED,false);}
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        var player=owner().map(id->((ServerLevel)level()).getServer().getPlayerList().getPlayer(id)).orElse(null);
        advance(player);
    }
    /** 論理サーバーの1段。決定的なワールド内での飛行のテストでも使う。 */
    public void advance(ServerPlayer player){
        if(level().isClientSide||isRemoved())return;
        var ledger=CoinLedger.get((ServerLevel)level());var owner=owner().orElse(null);
        if(owner==null||!ledger.isActive(owner,getUUID())){discard();return;}
        if(resumeAsReturn||player==null||!owner.equals(player.getUUID())||!player.isAlive()||player.isSpectator()||player.level()!=level()){
            ledger.requestReturn(owner,getUUID());discard();return;
        }
        age++;double velocity=getDeltaMovement().y-.06;
        Vec3 next=new Vec3(player.getX(),getY()+velocity,player.getZ());
        if(!Double.isFinite(next.y)||level().isOutsideBuildHeight(BlockPos.containing(next))
                ||!level().hasChunkAt(BlockPos.containing(next))||!level().getWorldBorder().isWithinBounds(BlockPos.containing(next))){
            ledger.requestReturn(owner,getUUID());discard();return;
        }
        // 原作のRigidbodyは衝突のイベントを送るが、コインに止める処理は無い。
        // そのため縦の更新はバニラの衝突移動ではなく、位置を直接使う。
        setDeltaMovement(0,velocity,0);setPos(next);hurtMarked=true;maximumHeight=Math.max(maximumHeight,next.y);
        double height=maximumHeight-initialHeight;
        double progress=velocity>0?(.92-velocity)/.92*.5:height<=1e-8?1:.5+(maximumHeight-next.y)/height*.5;
        entityData.set(PROGRESS,(float)Math.max(0,Math.min(1,progress)));
        if((next.y<player.getY()&&velocity<0)||age>120){
            // 原作のクライアントは、プレイヤーが求めていれば、コインが戻ってくるときに表か裏かを言う。
            level().broadcastEntityEvent(this,LANDED);
            ledger.requestReturn(owner,getUUID());ledger.refundDue(player);discard();
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());
        // タイミングの厳しいQTEをやり直すのは安全ではない。元の保管トークンを返却する。
        resumeAsReturn=true;setNoGravity(true);setInvulnerable(true);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){owner().ifPresent(id->tag.putUUID("Owner",id));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
