package com.example.testgame;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.particle.ParticleEmitter;
import com.almasb.fxgl.particle.ParticleEmitters;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import java.util.Map;

import static com.almasb.fxgl.dsl.FXGL.*;

public class SwordGameApp extends GameApplication {

    public enum EntityType {
        PLAYER1, PLAYER2, SWORD1, SWORD2, PARRY_P1, PARRY_P2
    }

    public enum PlayerState {
        IDLE, ATTACKING, PARRYING, STUNNED
    }

    private Entity player1;
    private Entity player2;
    private Entity sword1;
    private Entity sword2;

    private PlayerState p1State = PlayerState.IDLE;
    private PlayerState p2State = PlayerState.IDLE;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("Simple Sword Fight");
        settings.setVersion("0.1");
    }

    @Override
    protected void initGameVars(Map<String, Object> vars) {
        vars.put("score1", 0);
        vars.put("score2", 0);
        vars.put("slowmo", false);
    }

    @Override
    protected void initGame() {
        getGameWorld().addEntityFactory(new SwordGameFactory());

        player1 = spawn("player", 100, 300);
        player1.setType(EntityType.PLAYER1);

        player2 = spawn("player", 660, 300);
        player2.setType(EntityType.PLAYER2);
        player2.setScaleX(-1);
        
        // Initial swords (hidden/inactive)
        sword1 = spawn("sword", -100, -100);
        sword1.setType(EntityType.SWORD1);
        sword1.setVisible(false);

        sword2 = spawn("sword", -100, -100);
        sword2.setType(EntityType.SWORD2);
        sword2.setVisible(false);
    }

    @Override
    protected void initInput() {
        // Player 1
        getInput().addAction(new UserAction("P1 Move Left") {
            @Override
            protected void onAction() { if (!getb("slowmo")) player1.getComponent(PlayerComponent.class).moveLeft(); }
        }, KeyCode.A);
        getInput().addAction(new UserAction("P1 Move Right") {
            @Override
            protected void onAction() { if (!getb("slowmo")) player1.getComponent(PlayerComponent.class).moveRight(); }
        }, KeyCode.D);
        getInput().addAction(new UserAction("P1 Attack") {
            @Override
            protected void onActionBegin() { 
                if (p1State == PlayerState.IDLE && !getb("slowmo")) attack(player1, sword1, (int) player1.getScaleX()); 
            }
        }, MouseButton.PRIMARY);

        getInput().addAction(new UserAction("P1 Parry") {
            @Override
            protected void onActionBegin() {
                if (p1State == PlayerState.IDLE && !getb("slowmo")) parry(player1, 1);
            }
        }, MouseButton.SECONDARY);
    }

    private void parry(Entity player, int playerNum) {
        if (playerNum == 1) {
            p1State = PlayerState.PARRYING;
            player.getComponent(PlayerComponent.class).setParrying(true);
        } else {
            p2State = PlayerState.PARRYING;
            player.getComponent(PlayerComponent.class).setParrying(true);
        }

        player.getViewComponent().setOpacity(0.8);

        // Spawn larger parry shield entity
        Entity parryShield = spawn("parry_shield", player.getX() - 20, player.getY() - 10);
        parryShield.setType(playerNum == 1 ? EntityType.PARRY_P1 : EntityType.PARRY_P2);
        parryShield.getViewComponent().setOpacity(0.3); // Semi-transparent blue
        
        // Make shield follow player
        parryShield.xProperty().bind(player.xProperty().subtract(20));
        parryShield.yProperty().bind(player.yProperty().subtract(10));

        runOnce(() -> {
            parryShield.removeFromWorld();
            if (playerNum == 1) {
                if (p1State == PlayerState.PARRYING) {
                    p1State = PlayerState.IDLE;
                    player.getComponent(PlayerComponent.class).setParrying(false);
                }
            } else {
                if (p2State == PlayerState.PARRYING) {
                    p2State = PlayerState.IDLE;
                    player.getComponent(PlayerComponent.class).setParrying(false);
                }
            }
            if (player.getComponent(PlayerComponent.class) != null) {
                player.getViewComponent().setOpacity(1.0);
            }
        }, javafx.util.Duration.seconds(0.6));
    }

    @Override
    protected void onUpdate(double tpf) {
        if (getb("slowmo")) return;
        
        // AI for Player 2
        if (p2State == PlayerState.STUNNED) return;

        double distance = player1.getX() - player2.getX();
        double absDist = Math.abs(distance);
        
        // Advanced AI logic
        if (p2State == PlayerState.IDLE) {
            // Reaction to player attack - slightly faster reaction
            if (sword1.isVisible() && absDist < 120 && Math.random() < 0.1) {
                parry(player2, 2);
                return;
            }

            if (absDist > 120) {
                // Too far, move closer
                if (distance > 0) {
                    player2.getComponent(PlayerComponent.class).moveRight();
                } else {
                    player2.getComponent(PlayerComponent.class).moveLeft();
                }
            } else if (absDist < 60) {
                // Too close, move away or attack
                if (Math.random() < 0.05) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else {
                    if (distance > 0) {
                        player2.getComponent(PlayerComponent.class).moveLeft();
                    } else {
                        player2.getComponent(PlayerComponent.class).moveRight();
                    }
                }
            } else {
                // Optimal range, wait, feint or attack
                if (Math.random() < 0.04) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else if (Math.random() < 0.02) {
                    // Feint/Movement
                    if (distance > 0) {
                        player2.getComponent(PlayerComponent.class).moveRight();
                    } else {
                        player2.getComponent(PlayerComponent.class).moveLeft();
                    }
                }
            }
        }
    }

    private void attack(Entity player, Entity sword, int direction) {
        if (sword.isVisible()) return;

        int playerNum = player.getType() == EntityType.PLAYER1 ? 1 : 2;
        if (playerNum == 1) {
            p1State = PlayerState.ATTACKING;
            player.getComponent(PlayerComponent.class).setAttacking(true);
        } else {
            p2State = PlayerState.ATTACKING;
            player.getComponent(PlayerComponent.class).setAttacking(true);
        }

        sword.setPosition(player.getX() + (direction > 0 ? 55 : -45), player.getY() + 25);
        sword.setVisible(true);
        sword.setRotation(direction > 0 ? -45 : 45);

        // Simple swing animation: rotate the sword
        animationBuilder()
                .duration(javafx.util.Duration.seconds(0.2))
                .rotate(sword)
                .from(direction > 0 ? -45 : 45)
                .to(direction > 0 ? 45 : -45)
                .buildAndPlay();
        
        runOnce(() -> {
            sword.setVisible(false);
            sword.setPosition(-100, -100);
            if (playerNum == 1) {
                if (p1State == PlayerState.ATTACKING) {
                    p1State = PlayerState.IDLE;
                    player.getComponent(PlayerComponent.class).setAttacking(false);
                }
            } else {
                if (p2State == PlayerState.ATTACKING) {
                    p2State = PlayerState.IDLE;
                    player.getComponent(PlayerComponent.class).setAttacking(false);
                }
            }
        }, javafx.util.Duration.seconds(0.2));
    }

    @Override
    protected void initPhysics() {
        onCollisionBegin(EntityType.SWORD1, EntityType.PARRY_P2, (sword, shield) -> {
            spawnSparks(sword.getCenter());
            getGameScene().getViewport().shake(0.2, 5);
            
            // Knockback
            player1.translateX(-20);
            player2.translateX(10);

            sword.setVisible(false);
            sword.setPosition(-100, -100);
            
            // Only stun the attacker (P1)
            stun(player1, 1);
            
            // Reset P2 state so they can counter-attack immediately
            p2State = PlayerState.IDLE;
            player2.getComponent(PlayerComponent.class).setParrying(false);
            player2.getViewComponent().setOpacity(1.0);
        });

        onCollisionBegin(EntityType.SWORD2, EntityType.PARRY_P1, (sword, shield) -> {
            spawnSparks(sword.getCenter());
            getGameScene().getViewport().shake(0.2, 5);

            // Knockback
            player2.translateX(20);
            player1.translateX(-10);

            sword.setVisible(false);
            sword.setPosition(-100, -100);
            
            // Only stun the attacker (P2)
            stun(player2, 2);
            
            // Reset P1 state so they can counter-attack immediately
            p1State = PlayerState.IDLE;
            player1.getComponent(PlayerComponent.class).setParrying(false);
            player1.getViewComponent().setOpacity(1.0);
        });

        onCollisionBegin(EntityType.SWORD1, EntityType.PLAYER2, (sword, p2) -> {
            if (p2State != PlayerState.STUNNED && p2State != PlayerState.PARRYING) {
                spawnHitEffect(p2.getCenter());
                getGameScene().getViewport().shake(0.5, 5);
                inc("score1", +1);
                
                // Brief slow motion
                set("slowmo", true);
                getGameTimer().runOnceAfter(() -> {
                    set("slowmo", false);
                    resetPlayers();
                }, javafx.util.Duration.seconds(0.05));
            }
        });
        onCollisionBegin(EntityType.SWORD2, EntityType.PLAYER1, (sword, p1) -> {
            if (p1State != PlayerState.STUNNED && p1State != PlayerState.PARRYING) {
                spawnHitEffect(p1.getCenter());
                getGameScene().getViewport().shake(0.5, 5);
                inc("score2", +1);

                // Brief slow motion
                set("slowmo", true);
                getGameTimer().runOnceAfter(() -> {
                    set("slowmo", false);
                    resetPlayers();
                }, javafx.util.Duration.seconds(0.05));
            }
        });
    }

    private void spawnSparks(javafx.geometry.Point2D point) {
        ParticleEmitter emitter = ParticleEmitters.newExplosionEmitter(10);
        emitter.setStartColor(Color.GOLD);
        emitter.setEndColor(Color.YELLOW);
        emitter.setMaxEmissions(1);
        emitter.setNumParticles(15);
        
        Entity sparks = entityBuilder()
                .at(point)
                .with(new com.almasb.fxgl.particle.ParticleComponent(emitter))
                .buildAndAttach();
        
        runOnce(() -> sparks.removeFromWorld(), javafx.util.Duration.seconds(0.5));
    }

    private void spawnHitEffect(javafx.geometry.Point2D point) {
        ParticleEmitter emitter = ParticleEmitters.newExplosionEmitter(20);
        emitter.setStartColor(Color.RED);
        emitter.setEndColor(Color.DARKRED);
        emitter.setMaxEmissions(1);
        emitter.setNumParticles(25);

        Entity hit = entityBuilder()
                .at(point)
                .with(new com.almasb.fxgl.particle.ParticleComponent(emitter))
                .buildAndAttach();

        runOnce(() -> hit.removeFromWorld(), javafx.util.Duration.seconds(0.5));
    }

    private void stun(Entity player, int playerNum) {
        if (playerNum == 1) {
            p1State = PlayerState.STUNNED;
            player.getComponent(PlayerComponent.class).setAttacking(false);
            player.getComponent(PlayerComponent.class).setParrying(false);
        } else {
            p2State = PlayerState.STUNNED;
            player.getComponent(PlayerComponent.class).setAttacking(false);
            player.getComponent(PlayerComponent.class).setParrying(false);
        }

        player.getViewComponent().setOpacity(0.2);

        double stunDuration = playerNum == 1 ? 1.0 : (0.5 + Math.random());

        runOnce(() -> {
            if (playerNum == 1) p1State = PlayerState.IDLE;
            else p2State = PlayerState.IDLE;
            if (player.getComponent(PlayerComponent.class) != null) {
                player.getViewComponent().setOpacity(1.0);
            }
        }, javafx.util.Duration.seconds(stunDuration));
    }

    private void resetPlayers() {
        p1State = PlayerState.IDLE;
        p2State = PlayerState.IDLE;
        player1.getComponent(PlayerComponent.class).setAttacking(false);
        player1.getComponent(PlayerComponent.class).setParrying(false);
        player2.getComponent(PlayerComponent.class).setAttacking(false);
        player2.getComponent(PlayerComponent.class).setParrying(false);
        player1.getViewComponent().setOpacity(1.0);
        player2.getViewComponent().setOpacity(1.0);
        player1.setPosition(100, 300);
        player1.setScaleX(1);
        player2.setPosition(660, 300);
        player2.setScaleX(-1);
        sword1.setVisible(false);
        sword1.setPosition(-100, -100);
        sword2.setVisible(false);
        sword2.setPosition(-100, -100);
    }

    @Override
    protected void initUI() {
        Text score1Text = getUIFactoryService().newText("", Color.BLUE, 24);
        score1Text.setTranslateX(50);
        score1Text.setTranslateY(50);
        score1Text.textProperty().bind(getip("score1").asString("P1: %d"));

        Text score2Text = getUIFactoryService().newText("", Color.RED, 24);
        score2Text.setTranslateX(650);
        score2Text.setTranslateY(50);
        score2Text.textProperty().bind(getip("score2").asString("P2: %d"));

        addUINode(score1Text);
        addUINode(score2Text);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
