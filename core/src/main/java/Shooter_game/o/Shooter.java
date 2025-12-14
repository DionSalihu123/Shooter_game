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
    private static final int LEVELS = 5;
    private static final int BASE_ENEMIES = 20;

    // ================== RENDERING ==================
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private Texture circleTexture;

    // ================== GAME OBJECTS ==================
    private Player player;
    private Array<Bullet> bullets;
    private Array<Enemy> enemies;

    // ================== HUD ==================
    private BitmapFont fontBig, fontMed, fontSmall;
    private GlyphLayout[] hudCache = new GlyphLayout[4];

    // ================== GAME STATE ==================
    private int level = 1;
    private int score = 0;
    private float timer = 60f;
    private int enemiesToSpawn = BASE_ENEMIES;
    private int enemiesSpawned = 0;
    private long lastShotTime = 0;
    private long lastSpawnTime = 0;
    private String gameState = "playing"; // playing | win | lose

    @Override
    public void create() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, WIDTH, HEIGHT);
        camera.update();

        batch = new SpriteBatch();
        circleTexture = createCircleTexture(64);

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

        timer -= delta;
        if (timer < 0) timer = 0;

        // Spawn enemies
        if (enemiesSpawned < enemiesToSpawn &&
                TimeUtils.millis() - lastSpawnTime > SPAWN_INTERVAL_MS) {
            enemies.add(new Enemy());
            enemiesSpawned++;
            lastSpawnTime = TimeUtils.millis();
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

        // Level complete
        if (timer <= 0 && enemies.isEmpty()) {
            if (level < LEVELS) {
                level++;
                enemiesToSpawn += 5;
                timer = Math.max(20, 60 - level * 10);
                enemiesSpawned = 0;
                updateHud();
            } else gameState = "win";
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

        if (!gameState.equals("playing") && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT))
            Gdx.app.exit();
    }

    private void drawEndScreen() {
        String title = gameState.equals("win") ? "YOU WIN" : "GAME OVER";
        String sub = gameState.equals("win") ? "All levels survived" : "You were caught";

        GlyphLayout t = new GlyphLayout(fontBig, title);
        GlyphLayout s = new GlyphLayout(fontMed, sub);

        fontBig.setColor(gameState.equals("win") ? Color.GREEN : Color.RED);
        fontBig.draw(batch, t, WIDTH / 2f - t.width / 2f, HEIGHT / 2f + 60);
        fontMed.draw(batch, s, WIDTH / 2f - s.width / 2f, HEIGHT / 2f);
    }

    private void updateHud() {
        hudCache[0].setText(fontSmall, "Level: " + level + "/" + LEVELS);
        hudCache[1].setText(fontSmall, "Time: " + (int) timer);
        hudCache[2].setText(fontSmall, "Enemies: " + enemies.size);
        hudCache[3].setText(fontSmall, "Score: " + score);
    }

    @Override
    public void dispose() {
        batch.dispose();
        circleTexture.dispose();
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

    // ================== INNER CLASSES ==================

    class Player {
        float x, y, angle;
        Circle circle = new Circle();

        Player(float x, float y) {
            this.x = x;
            this.y = y;
            circle.radius = 24;
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
            speed = ENEMY_BASE_SPEED + level * 12;
            float a = MathUtils.random(0f, MathUtils.PI2);
            float d = MathUtils.random(600, 900);
            x = WIDTH / 2f + MathUtils.cos(a) * d;
            y = HEIGHT / 2f + MathUtils.sin(a) * d;
            circle.radius = 18;
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

