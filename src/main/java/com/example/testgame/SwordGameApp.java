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

    public enum NetMode {
        SINGLE_PLAYER, SERVER, CLIENT
    }

    private NetMode netMode = NetMode.SINGLE_PLAYER;
    private com.almasb.fxgl.net.Connection<com.almasb.fxgl.core.serialization.Bundle> connection;

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

        // Network selection
        runOnce(() -> {
            getDialogService().showConfirmationBox("Start as Multiplayer?", result -> {
                if (result) {
                    getDialogService().showConfirmationBox("Host a game? (No to Join)", isHost -> {
                        if (isHost) {
                            initServer();
                        } else {
                            getDialogService().showInputBox("Enter Host IP", ip -> {
                                if (ip != null && !ip.isEmpty()) {
                                    initClient(ip);
                                } else {
                                    netMode = NetMode.SINGLE_PLAYER;
                                }
                            });
                        }
                    });
                } else {
                    netMode = NetMode.SINGLE_PLAYER;
                }
            });
        }, javafx.util.Duration.seconds(0.1));
    }

    private void initServer() {
        netMode = NetMode.SERVER;
        var server = getNetService().newTCPServer(55555);

        // Retrieve local IP address for display
        String hostAddress = "Unknown";
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        hostAddress = addr.getHostAddress();
                        break;
                    }
                }
                if (!"Unknown".equals(hostAddress)) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        final String finalHostAddress = hostAddress;

        Text hostText = new Text("Hosting at IP: " + hostAddress);
        hostText.setFill(Color.BLUE);
        hostText.setFont(getUIFactoryService().newFont(16));
        hostText.setX(50);
        hostText.setY(80); // Below P1 score which is at Y=50
        addUINode(hostText);

        server.setOnConnected(conn -> {
            connection = conn;
            // Update UI to show someone joined
            runOnce(() -> {
                hostText.setText("Client joined! Hosting at IP: " + finalHostAddress);
                getNotificationService().pushNotification("Client joined!");
            }, javafx.util.Duration.seconds(0.1));

            conn.addMessageHandler((c, bundle) -> {
                // Server receives input from Client (Player 2)
                String action = bundle.get("action");
                if ("MOVE_LEFT".equals(action)) {
                    if (!getb("slowmo") && p2State != PlayerState.STUNNED) {
                        player2.getComponent(PlayerComponent.class).moveLeft();
                    }
                } else if ("MOVE_RIGHT".equals(action)) {
                    if (!getb("slowmo") && p2State != PlayerState.STUNNED) {
                        player2.getComponent(PlayerComponent.class).moveRight();
                    }
                } else if ("ATTACK".equals(action)) {
                    if (p2State == PlayerState.IDLE && !getb("slowmo")) {
                        attack(player2, sword2, (int) Math.signum(player1.getX() - player2.getX()));
                    }
                } else if ("PARRY".equals(action)) {
                    if (p2State == PlayerState.IDLE && !getb("slowmo")) {
                        parry(player2, 2);
                    }
                }
            });

            // No explicit setOnDisconnected in FXGL Connection
            // We can check connection.isConnected() in onUpdate if needed,
            // or just let it fail silently and handle it when sending.
        });

        try {
            server.startAsync();
        } catch (Exception e) {
            hostText.setText("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initClient(String ip) {
        netMode = NetMode.CLIENT;
        var client = getNetService().newTCPClient(ip, 55555);
        client.setOnConnected(conn -> {
            connection = conn;
            runOnce(() -> {
                getNotificationService().pushNotification("Connected to Host at " + ip);
            }, javafx.util.Duration.seconds(0.1));
            conn.addMessageHandler((c, bundle) -> {
                // Client receives state from Server
                double p1X = bundle.get("p1X");
                double p1Y = bundle.get("p1Y");
                double p1Scale = bundle.get("p1Scale");
                double p2X = bundle.get("p2X");
                double p2Y = bundle.get("p2Y");
                double p2Scale = bundle.get("p2Scale");
                boolean s1Vis = bundle.get("s1Vis");
                double s1X = bundle.get("s1X");
                double s1Y = bundle.get("s1Y");
                double s1Rot = bundle.get("s1Rot");
                boolean s2Vis = bundle.get("s2Vis");
                double s2X = bundle.get("s2X");
                double s2Y = bundle.get("s2Y");
                double s2Rot = bundle.get("s2Rot");
                int score1 = bundle.get("score1");
                int score2 = bundle.get("score2");
                boolean slowmo = bundle.get("slowmo");
                double p1Opac = bundle.get("p1Opac");
                double p2Opac = bundle.get("p2Opac");
                boolean p1Attack = bundle.get("p1Attack");
                boolean p1Parry = bundle.get("p1Parry");
                boolean p2Attack = bundle.get("p2Attack");
                boolean p2Parry = bundle.get("p2Parry");

                player1.setPosition(p1X, p1Y);
                player1.setScaleX(p1Scale);
                player1.getViewComponent().setOpacity(p1Opac);
                player1.getComponent(PlayerComponent.class).setAttacking(p1Attack);
                player1.getComponent(PlayerComponent.class).setParrying(p1Parry);

                player2.setPosition(p2X, p2Y);
                player2.setScaleX(p2Scale);
                player2.getViewComponent().setOpacity(p2Opac);
                player2.getComponent(PlayerComponent.class).setAttacking(p2Attack);
                player2.getComponent(PlayerComponent.class).setParrying(p2Parry);

                sword1.setVisible(s1Vis);
                sword1.setPosition(s1X, s1Y);
                sword1.setRotation(s1Rot);
                sword2.setVisible(s2Vis);
                sword2.setPosition(s2X, s2Y);
                sword2.setRotation(s2Rot);
                set("score1", score1);
                set("score2", score2);
                set("slowmo", slowmo);

                // Additional visual feedback for states could be added here
                // but for now, position and visibility are the key.
            });

            // No explicit setOnDisconnected in FXGL Connection
        });

        try {
            client.connectAsync();
        } catch (Exception e) {
            getDialogService().showMessageBox("Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void initInput() {
        // Player 1
        getInput().addAction(new UserAction("P1 Move Left") {
            @Override
            protected void onAction() {
                if (netMode == NetMode.CLIENT) return;
                if (!getb("slowmo")) player1.getComponent(PlayerComponent.class).moveLeft();
            }
        }, KeyCode.A);
        getInput().addAction(new UserAction("P1 Move Right") {
            @Override
            protected void onAction() {
                if (netMode == NetMode.CLIENT) return;
                if (!getb("slowmo")) player1.getComponent(PlayerComponent.class).moveRight();
            }
        }, KeyCode.D);
        getInput().addAction(new UserAction("P1 Attack") {
            @Override
            protected void onActionBegin() {
                if (netMode == NetMode.CLIENT) return;
                if (p1State == PlayerState.IDLE && !getb("slowmo")) attack(player1, sword1, (int) player1.getScaleX());
            }
        }, MouseButton.PRIMARY);

        getInput().addAction(new UserAction("P1 Parry") {
            @Override
            protected void onActionBegin() {
                if (netMode == NetMode.CLIENT) return;
                if (p1State == PlayerState.IDLE && !getb("slowmo")) parry(player1, 1);
            }
        }, MouseButton.SECONDARY);

        // Player 2 / Client Controls
        getInput().addAction(new UserAction("P2 Move Left") {
            @Override
            protected void onAction() {
                if (netMode == NetMode.CLIENT) {
                    if (connection != null && connection.isConnected()) {
                        var bundle = new com.almasb.fxgl.core.serialization.Bundle("Input");
                        bundle.put("action", "MOVE_LEFT");
                        connection.send(bundle);
                    }
                }
            }
        }, KeyCode.LEFT);
        getInput().addAction(new UserAction("P2 Move Right") {
            @Override
            protected void onAction() {
                if (netMode == NetMode.CLIENT) {
                    if (connection != null && connection.isConnected()) {
                        var bundle = new com.almasb.fxgl.core.serialization.Bundle("Input");
                        bundle.put("action", "MOVE_RIGHT");
                        connection.send(bundle);
                    }
                }
            }
        }, KeyCode.RIGHT);
        getInput().addAction(new UserAction("P2 Attack") {
            @Override
            protected void onActionBegin() {
                if (netMode == NetMode.CLIENT) {
                    if (connection != null && connection.isConnected()) {
                        var bundle = new com.almasb.fxgl.core.serialization.Bundle("Input");
                        bundle.put("action", "ATTACK");
                        connection.send(bundle);
                    }
                }
            }
        }, KeyCode.ENTER);
        getInput().addAction(new UserAction("P2 Parry") {
            @Override
            protected void onActionBegin() {
                if (netMode == NetMode.CLIENT) {
                    if (connection != null && connection.isConnected()) {
                        var bundle = new com.almasb.fxgl.core.serialization.Bundle("Input");
                        bundle.put("action", "PARRY");
                        connection.send(bundle);
                    }
                }
            }
        }, KeyCode.NUMPAD0);
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

        if (netMode == NetMode.SERVER && connection != null) {
            if (!connection.isConnected()) {
                connection = null;
                return;
            }
            var bundle = new com.almasb.fxgl.core.serialization.Bundle("State");
            bundle.put("p1X", player1.getX());
            bundle.put("p1Y", player1.getY());
            bundle.put("p1Scale", player1.getScaleX());
            bundle.put("p2X", player2.getX());
            bundle.put("p2Y", player2.getY());
            bundle.put("p2Scale", player2.getScaleX());
            bundle.put("s1Vis", sword1.isVisible());
            bundle.put("s1X", sword1.getX());
            bundle.put("s1Y", sword1.getY());
            bundle.put("s1Rot", sword1.getRotation());
            bundle.put("s2Vis", sword2.isVisible());
            bundle.put("s2X", sword2.getX());
            bundle.put("s2Y", sword2.getY());
            bundle.put("s2Rot", sword2.getRotation());
            bundle.put("score1", geti("score1"));
            bundle.put("score2", geti("score2"));
            bundle.put("slowmo", getb("slowmo"));
            bundle.put("p1Opac", player1.getViewComponent().getOpacity());
            bundle.put("p2Opac", player2.getViewComponent().getOpacity());
            bundle.put("p1Attack", player1.getComponent(PlayerComponent.class).isAttacking());
            bundle.put("p1Parry", player1.getComponent(PlayerComponent.class).isParrying());
            bundle.put("p2Attack", player2.getComponent(PlayerComponent.class).isAttacking());
            bundle.put("p2Parry", player2.getComponent(PlayerComponent.class).isParrying());
            connection.send(bundle);
        }

        if (netMode != NetMode.SINGLE_PLAYER) return;

        // AI for Player 2
        if (p2State == PlayerState.STUNNED) return;

        double distance = player1.getX() - player2.getX();
        double absDist = Math.abs(distance);
        
        // Advanced AI logic
        if (p2State == PlayerState.IDLE) {
            // Reaction to player attack - more aggressive parry/reaction
            if (sword1.isVisible() && absDist < 120 && Math.random() < 0.25) {
                parry(player2, 2);
                return;
            }

            if (absDist > 80) {
                // Too far, move closer (more aggressively)
                if (distance > 0) {
                    player2.getComponent(PlayerComponent.class).moveRight();
                } else {
                    player2.getComponent(PlayerComponent.class).moveLeft();
                }
            } else if (absDist < 40) {
                // Too close, move away or attack (higher attack probability)
                if (Math.random() < 0.15) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else {
                    // Less likely to just move away, might still stay close
                    if (Math.random() < 0.5) {
                        if (distance > 0) {
                            player2.getComponent(PlayerComponent.class).moveLeft();
                        } else {
                            player2.getComponent(PlayerComponent.class).moveRight();
                        }
                    }
                }
            } else {
                // Optimal range, wait, feint or attack (higher attack/feint probabilities)
                if (Math.random() < 0.1) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else if (Math.random() < 0.05) {
                    // Aggressive feinting
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
            if (netMode == NetMode.CLIENT) return;
            spawnSparks(sword.getCenter());
            getGameScene().getViewport().shake(0.2, 0.2);
            
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
            if (netMode == NetMode.CLIENT) return;
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
            if (netMode == NetMode.CLIENT) return;
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
            if (netMode == NetMode.CLIENT) return;
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
