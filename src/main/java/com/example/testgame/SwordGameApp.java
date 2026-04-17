package com.example.testgame;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import java.util.Map;

import static com.almasb.fxgl.dsl.FXGL.*;

public class SwordGameApp extends GameApplication {

    public enum EntityType {
        PLAYER1, PLAYER2, SWORD1, SWORD2
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
    }

    @Override
    protected void initGame() {
        getGameWorld().addEntityFactory(new SwordGameFactory());

        player1 = spawn("player", 100, 300);
        player1.setType(EntityType.PLAYER1);
        player1.getViewComponent().addChild(texture("spritesheet.png"));

        player2 = spawn("player", 660, 300);
        player2.setType(EntityType.PLAYER2);
        player2.getViewComponent().addChild(texture("spritesheet.png"));
        
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
            protected void onAction() { player1.translateX(-5); }
        }, KeyCode.A);
        getInput().addAction(new UserAction("P1 Move Right") {
            @Override
            protected void onAction() { player1.translateX(5); }
        }, KeyCode.D);
        getInput().addAction(new UserAction("P1 Attack") {
            @Override
            protected void onActionBegin() { 
                if (p1State == PlayerState.IDLE) attack(player1, sword1, 1); 
            }
        }, MouseButton.PRIMARY);

        getInput().addAction(new UserAction("P1 Parry") {
            @Override
            protected void onActionBegin() {
                if (p1State == PlayerState.IDLE) parry(player1, 1);
            }
        }, MouseButton.SECONDARY);
    }

    private void parry(Entity player, int playerNum) {
        if (playerNum == 1) p1State = PlayerState.PARRYING;
        else p2State = PlayerState.PARRYING;

        player.getViewComponent().setOpacity(0.5);

        runOnce(() -> {
            if (playerNum == 1) p1State = PlayerState.IDLE;
            else p2State = PlayerState.IDLE;
            player.getViewComponent().setOpacity(1.0);
        }, javafx.util.Duration.seconds(0.3));
    }

    @Override
    protected void onUpdate(double tpf) {
        // AI for Player 2
        if (p2State == PlayerState.STUNNED) return;

        double distance = player1.getX() - player2.getX();
        double absDist = Math.abs(distance);
        
        // Advanced AI logic
        if (p2State == PlayerState.IDLE) {
            // Reaction to player attack
            if (sword1.isVisible() && absDist < 100 && Math.random() < 0.05) {
                parry(player2, 2);
                return;
            }

            if (absDist > 120) {
                // Too far, move closer
                player2.translateX(distance > 0 ? 3 : -3);
            } else if (absDist < 80) {
                // Too close, move away or attack
                if (Math.random() < 0.02) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else {
                    player2.translateX(distance > 0 ? -2 : 2);
                }
            } else {
                // Optimal range, wait or attack
                if (Math.random() < 0.03) {
                    attack(player2, sword2, (int) Math.signum(distance));
                } else if (Math.random() < 0.01) {
                    player2.translateX(distance > 0 ? 2 : -2); // Shuffle
                }
            }
        }
    }

    private void attack(Entity player, Entity sword, int direction) {
        if (sword.isVisible()) return;

        int playerNum = player.getType() == EntityType.PLAYER1 ? 1 : 2;
        if (playerNum == 1) p1State = PlayerState.ATTACKING;
        else p2State = PlayerState.ATTACKING;

        sword.setPosition(player.getX() + (direction > 0 ? 40 : -30), player.getY() + 10);
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
                if (p1State == PlayerState.ATTACKING) p1State = PlayerState.IDLE;
            } else {
                if (p2State == PlayerState.ATTACKING) p2State = PlayerState.IDLE;
            }
        }, javafx.util.Duration.seconds(0.2));
    }

    @Override
    protected void initPhysics() {
        onCollisionBegin(EntityType.SWORD1, EntityType.PLAYER2, (sword, p2) -> {
            if (p2State == PlayerState.PARRYING) {
                stun(player2, 2);
                sword.setVisible(false);
                sword.setPosition(-100, -100);
                p1State = PlayerState.STUNNED; // Attacker gets stunned on parry
                stun(player1, 1);
            } else {
                inc("score1", +1);
                resetPlayers();
            }
        });
        onCollisionBegin(EntityType.SWORD2, EntityType.PLAYER1, (sword, p1) -> {
            if (p1State == PlayerState.PARRYING) {
                stun(player1, 1);
                sword.setVisible(false);
                sword.setPosition(-100, -100);
                p2State = PlayerState.STUNNED;
                stun(player2, 2);
            } else {
                inc("score2", +1);
                resetPlayers();
            }
        });
    }

    private void stun(Entity player, int playerNum) {
        if (playerNum == 1) p1State = PlayerState.STUNNED;
        else p2State = PlayerState.STUNNED;

        player.getViewComponent().setOpacity(0.2);

        runOnce(() -> {
            if (playerNum == 1) p1State = PlayerState.IDLE;
            else p2State = PlayerState.IDLE;
            player.getViewComponent().setOpacity(1.0);
        }, javafx.util.Duration.seconds(1.0));
    }

    private void resetPlayers() {
        p1State = PlayerState.IDLE;
        p2State = PlayerState.IDLE;
        player1.getViewComponent().setOpacity(1.0);
        player2.getViewComponent().setOpacity(1.0);
        player1.setPosition(100, 300);
        player2.setPosition(660, 300);
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
