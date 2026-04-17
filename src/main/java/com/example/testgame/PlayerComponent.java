package com.example.testgame;

import com.almasb.fxgl.entity.component.Component;
import com.almasb.fxgl.texture.Texture;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import static com.almasb.fxgl.dsl.FXGL.*;

public class PlayerComponent extends Component {

    private Texture texture;
    private Texture idleTexture, attackTexture, parryTexture;
    private boolean isAttacking = false;
    private boolean isParrying = false;

    public PlayerComponent() {
        idleTexture = texture("Idle.png", 100, 100);
        attackTexture = texture("Attack.png", 100, 100);
        parryTexture = texture("Parry.png", 100, 100);

        texture = idleTexture.copy();
    }

    @Override
    public void onAdded() {
        entity.getTransformComponent().setScaleOrigin(entity.getCenter());
        
        // Center 100x100 texture over 40x40 hitbox
        // Offset X = (100 - 40) / 2 = 30
        // Offset Y = (100 - 40) = 60
        texture.setTranslateX(-30); 
        texture.setTranslateY(-60); 
        entity.getViewComponent().addChild(texture);

        // Draw the hitbox (40x40 box)
        Rectangle hitboxView = new Rectangle(40, 40, Color.color(0, 1, 0, 0.3));
        hitboxView.setStroke(Color.GREEN);
        hitboxView.setStrokeWidth(2);
        entity.getViewComponent().addChild(hitboxView);
    }

    @Override
    public void onUpdate(double tpf) {
        if (isAttacking) {
            updateTexture(attackTexture);
        } else if (isParrying) {
            updateTexture(parryTexture);
        } else {
            updateTexture(idleTexture);
        }
    }

    private void updateTexture(Texture newTexture) {
        if (texture.getImage() != newTexture.getImage()) {
            entity.getViewComponent().removeChild(texture);
            texture = newTexture.copy();
            
            texture.setTranslateX(-30);
            texture.setTranslateY(-60);
            entity.getViewComponent().addChild(texture);
        }
        
        // Ensure scale origin is always center of the entity (40x40 box at 0,0 local)
        // entity.getCenter() returns (20,20) in local coordinates
        entity.getTransformComponent().setScaleOrigin(new javafx.geometry.Point2D(20, 20));
    }

    public void moveLeft() {
        if (isAttacking || isParrying) return;
        entity.translateX(-5);
        entity.setScaleX(-1);
    }

    public void moveRight() {
        if (isAttacking || isParrying) return;
        entity.translateX(5);
        entity.setScaleX(1);
    }

    public void attack() {
        if (isAttacking || isParrying) return;
        isAttacking = true;
        getGameTimer().runOnceAfter(() -> {
            isAttacking = false;
        }, Duration.seconds(0.3));
    }

    public void parry() {
        if (isAttacking || isParrying) return;
        isParrying = true;
        getGameTimer().runOnceAfter(() -> {
            isParrying = false;
        }, Duration.seconds(0.5));
    }

    public void setAttacking(boolean attacking) {
        isAttacking = attacking;
    }

    public void setParrying(boolean parrying) {
        isParrying = parrying;
    }

    public boolean isAttacking() {
        return isAttacking;
    }

    public boolean isParrying() {
        return isParrying;
    }
}
