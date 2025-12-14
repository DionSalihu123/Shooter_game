package Shooter_game.o;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;

public class Shooter extends ApplicationAdapter {
    // ================== SETTINGS ==================
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final float PLAYER_SPEED = 320f;
    private static final float BULLET_SPEED = 900f;
    private static final float ENEMY_BASE_SPEED = 80f;
    private static final int SHOOT_DELAY_MS = 180;
    private static final int SPAWN_INTERVAL_MS = 900;
    private static final int TOTAL_ENEMIES = 100; // fixed total enemies

    // ================== RENDERING ==================
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private Texture circleTexture;
    private Texture whitePixel; // used for UI rectangles

    // ================== GAME OBJECTS ==================
    private Player player;
    private Array<Bullet> bullets;
    private Array<Enemy> enemies;

    // ================== HUD ==================
    private BitmapFont fontBig, fontMed, fontSmall;
    private GlyphLayout[] hudCache = new GlyphLayout[3]; // Time, Killed, Score

    // ================== GAME STATE ==================
    private int score = 0;
    private int enemiesSpawned = 0;
    private int kills = 0;
    private long lastShotTime = 0;
    private long lastSpawnTime = 0;
    private String gameState = "playing"; // playing | win | lose

    // timer now counts up from 0
    private float elapsedTime = 0f;

    // enemy speed multiplier increases every 10 kills
    private float enemySpeedMultiplier = 1f;
    private final float SPEED_INCREASE_FACTOR = 1.25f; // change to tweak how much faster enemies get every 10 kills

    // UI button for Play Again
    private static final float BUTTON_W = 220f;
    private static final float BUTTON_H = 35f; // height of the button
    private static final float BUTTON_TOP_MARGIN = 60f; // increased margin between kills text and button
    private float buttonX = 0f, buttonY = 0f; // will be set each frame in drawEndScreen

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, WIDTH, HEIGHT);
        camera.update();

        batch = new SpriteBatch();
        circleTexture = createCircleTexture(64);
        whitePixel = createPixelTexture();

        player = new Player(WIDTH / 2f, HEIGHT / 2f);
        bullets = new Array<>();
        enemies = new Array<>();

        fontBig = new BitmapFont();
        fontBig.getData().setScale(4);
        fontBig.setColor(Color.GREEN);
        fontMed = new BitmapFont();
        fontMed.getData().setScale(2.5f);
        fontSmall = new BitmapFont();
        fontSmall.getData().setScale(1.8f);

        for (int i = 0; i < hudCache.length; i++) hudCache[i] = new GlyphLayout();
        updateHud();
    }

    private void update(float delta) {
        if (!gameState.equals("playing")) return;

        // timer counts up
        elapsedTime += delta;

        // Spawn enemies gradually until TOTAL_ENEMIES reached
        if (enemiesSpawned < TOTAL_ENEMIES &&
                TimeUtils.millis() - lastSpawnTime > SPAWN_INTERVAL_MS) {
            enemies.add(new Enemy());
            enemiesSpawned++;
            lastSpawnTime = TimeUtils.millis();
            updateHud();
        }

        player.update(delta);

        // Shooting
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            long now = TimeUtils.millis();
            if (now - lastShotTime > SHOOT_DELAY_MS) {
                bullets.add(new Bullet(player.x, player.y, player.angle));
                lastShotTime = now;
            }
        }

        // Update bullets
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(delta);
            if (b.isOffscreen()) bullets.removeIndex(i);
        }

        // Update enemies
        for (Enemy e : enemies) e.update(delta, player.x, player.y);

        // Bullet-enemy collisions
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            for (int j = enemies.size - 1; j >= 0; j--) {
                Enemy e = enemies.get(j);
                if (Intersector.overlaps(b.circle, e.circle)) {
                    bullets.removeIndex(i);
                    enemies.removeIndex(j);
                    score += 10;
                    kills++;
                    // every 10 kills increase speed multiplier for remaining enemies
                    if (kills % 10 == 0 && kills <= TOTAL_ENEMIES) {
                        enemySpeedMultiplier *= SPEED_INCREASE_FACTOR;
                        // apply new speed to all currently alive enemies
                        for (Enemy en : enemies) en.speed = ENEMY_BASE_SPEED * enemySpeedMultiplier;
                    }
                    updateHud();
                    break;
                }
            }
        }

        // Player-enemy collisions
        for (Enemy e : enemies) {
            if (Intersector.overlaps(player.circle, e.circle)) {
                gameState = "lose";
                return;
            }
        }

        // Win condition: killed all enemies
        if (kills >= TOTAL_ENEMIES) {
            gameState = "win";
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        update(delta);

        ScreenUtils.clear(0.53f, 0.81f, 0.92f, 1);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        player.draw(batch);
        for (Bullet b : bullets) b.draw(batch);
        for (Enemy e : enemies) e.draw(batch);
        for (int i = 0; i < hudCache.length; i++)
            fontSmall.draw(batch, hudCache[i], 20, HEIGHT - 20 - i * 35);

        if (!gameState.equals("playing")) drawEndScreen();

        batch.end();

        // handle input on end screen: if Play Again pressed, restart; otherwise do nothing.
        if (!gameState.equals("playing") && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector3 mp = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mp);
            if (isInsideButton(mp.x, mp.y)) {
                resetGame();
            }
        }
    }

    private boolean isInsideButton(float mx, float my) {
        return mx >= buttonX && mx <= buttonX + BUTTON_W && my >= buttonY && my <= buttonY + BUTTON_H;
    }

    private void drawEndScreen() {
        // Draw semi-transparent centered panel
        float panelW = 520f;
        float panelH = 260f;
        float panelX = WIDTH / 2f - panelW / 2f;
        float panelY = HEIGHT / 2f - panelH / 2f;

        // background dim
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(whitePixel, 0, 0, WIDTH, HEIGHT);
        batch.setColor(Color.WHITE);

        // panel background
        batch.setColor(0f, 0f, 0f, 0.75f);
        batch.draw(whitePixel, panelX, panelY, panelW, panelH);
        batch.setColor(Color.WHITE);

        String title = gameState.equals("win") ? "YOU WIN" : "GAME OVER";
        String sub = gameState.equals("win") ? "All enemies defeated" : "You were caught";
        String killsLine = "Enemies killed: " + kills + "/" + TOTAL_ENEMIES;

        GlyphLayout t = new GlyphLayout(fontBig, title);
        GlyphLayout s = new GlyphLayout(fontMed, sub);
        GlyphLayout k = new GlyphLayout(fontMed, killsLine);

        // title
        fontBig.setColor(gameState.equals("win") ? Color.GREEN : Color.RED);
        fontBig.draw(batch, t, WIDTH / 2f - t.width / 2f, panelY + panelH - 30);

        // subtitle
        fontMed.setColor(Color.WHITE);
        fontMed.draw(batch, s, WIDTH / 2f - s.width / 2f, panelY + panelH - 90);

        // kills (baseline)
        float killsY = panelY + panelH - 140;
        fontMed.draw(batch, k, WIDTH / 2f - k.width / 2f, killsY);

        // compute button position so it sits below the kills text with extra top margin
        buttonX = WIDTH / 2f - BUTTON_W / 2f;
        // place button below kills line with extra margin to avoid overlap:
        buttonY = killsY - BUTTON_TOP_MARGIN - BUTTON_H;

        // Draw Play Again button
        batch.setColor(0.15f, 0.55f, 0.9f, 1f);
        batch.draw(whitePixel, buttonX, buttonY, BUTTON_W, BUTTON_H);
        batch.setColor(Color.WHITE);

        GlyphLayout btn = new GlyphLayout(fontMed, "Play Again");
        // center text vertically in the button
        float textY = buttonY + BUTTON_H / 2f + btn.height / 2f;
        fontMed.setColor(Color.WHITE);
        fontMed.draw(batch, btn, buttonX + BUTTON_W / 2f - btn.width / 2f, textY);
    }

    private void updateHud() {
        hudCache[0].setText(fontSmall, "Time: " + (int) elapsedTime + "s");
        hudCache[1].setText(fontSmall, "Killed: " + kills + "/" + TOTAL_ENEMIES);
        hudCache[2].setText(fontSmall, "Score: " + score);
    }

    private void resetGame() {
        // reset core state
        score = 0;
        enemiesSpawned = 0;
        kills = 0;
        elapsedTime = 0f;
        enemySpeedMultiplier = 1f;
        lastShotTime = 0;
        lastSpawnTime = 0;
        gameState = "playing";

        // clear arrays
        bullets.clear();
        enemies.clear();

        // reset player position
        player = new Player(WIDTH / 2f, HEIGHT / 2f);

        updateHud();
    }

    @Override
    public void dispose() {
        batch.dispose();
        circleTexture.dispose();
        if (whitePixel != null) whitePixel.dispose();
        fontBig.dispose();
        fontMed.dispose();
        fontSmall.dispose();
    }

    private Texture createCircleTexture(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fillCircle(size / 2, size / 2, size / 2);
        Texture t = new Texture(p);
        p.dispose();
        return t;
    }

    private Texture createPixelTexture() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        return t;
    }

    // ================== INNER CLASSES ==================
    class Player {
        float x, y, angle;
        Circle circle = new Circle();

        Player(float x, float y) {
            this.x = x;
            this.y = y;
            circle.radius = 24;
            circle.setPosition(x, y);
        }

        void update(float delta) {
            Vector2 m = new Vector2();
            if (Gdx.input.isKeyPressed(Input.Keys.W)) m.y++;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) m.y--;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) m.x--;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) m.x++;
            if (m.len2() > 0) {
                m.nor().scl(PLAYER_SPEED * delta);
                x = MathUtils.clamp(x + m.x, 0, WIDTH);
                y = MathUtils.clamp(y + m.y, 0, HEIGHT);
            }
            Vector3 mouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(mouse);
            angle = new Vector2(mouse.x - x, mouse.y - y).angleDeg();
            circle.setPosition(x, y);
        }

        void draw(SpriteBatch b) {
            b.setColor(Color.GREEN);
            b.draw(circleTexture, x - 24, y - 24, 48, 48);
            b.setColor(Color.WHITE);
        }
    }

    class Bullet {
        float x, y;
        Vector2 vel = new Vector2();
        Circle circle = new Circle();

        Bullet(float x, float y, float a) {
            this.x = x;
            this.y = y;
            vel.set(1, 0).setAngleDeg(a).scl(BULLET_SPEED);
            circle.radius = 5;
            circle.setPosition(x, y);
        }

        void update(float delta) {
            x += vel.x * delta;
            y += vel.y * delta;
            circle.setPosition(x, y);
        }

        boolean isOffscreen() {
            return x < -20 || x > WIDTH + 20 || y < -20 || y > HEIGHT + 20;
        }

        void draw(SpriteBatch b) {
            b.setColor(Color.YELLOW);
            b.draw(circleTexture, x - 5, y - 5, 10, 10);
            b.setColor(Color.WHITE);
        }
    }

    class Enemy {
        float x, y, speed;
        Circle circle = new Circle();

        Enemy() {
            // Use base speed multiplied by current multiplier so later spawned enemies are faster
            speed = ENEMY_BASE_SPEED * enemySpeedMultiplier;
            float a = MathUtils.random(0f, MathUtils.PI2);
            float d = MathUtils.random(600, 900);
            x = WIDTH / 2f + MathUtils.cos(a) * d;
            y = HEIGHT / 2f + MathUtils.sin(a) * d;
            circle.radius = 18;
            circle.setPosition(x, y);
        }

        void update(float delta, float px, float py) {
            Vector2 dir = new Vector2(px - x, py - y);
            if (dir.len2() > 0) {
                dir.nor().scl(speed * delta);
                x += dir.x;
                y += dir.y;
            }
            circle.setPosition(x, y);
        }

        void draw(SpriteBatch b) {
            b.setColor(Color.RED);
            b.draw(circleTexture, x - 18, y - 18, 36, 36);
            b.setColor(Color.WHITE);
        }
    }
}
