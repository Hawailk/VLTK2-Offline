package com.vltk2.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

public final class MainMidlet extends MIDlet {
    private GameCanvas game;

    protected void startApp() {
        if (game == null) {
            game = new GameCanvas();
        }
        Display.getDisplay(this).setCurrent(game);
        game.start();
    }

    protected void pauseApp() {
        if (game != null) {
            game.pauseGame();
        }
    }

    protected void destroyApp(boolean unconditional) {
        if (game != null) {
            game.stopGame();
        }
    }

    private static final class GameCanvas extends Canvas implements Runnable {
        private static final int TILE = 32;
        private static final int WORLD_WIDTH = 2400;
        private static final int WORLD_HEIGHT = 1800;
        private static final int MAX_HP = 100;
        private static final int SKILL_COOLDOWN = 4200;
        private static final String STORE_NAME = "VLTK2Save";
        private static final int QUEST_GOAL = 8;
        private static final int PORTAL_X = 2140;
        private static final int PORTAL_Y = 1560;
        private static final String[] PLAYER_SPRITE = {
            "    111   ", "   111111 ", "   122221 ", "    222   ",
            "  0000000 ", "  0033300 ", " 033343300", "03333333300",
            "03333433300", " 00333300 ", "   5555   ", "  55  55  ",
            " 55    55 ", "55      55", "55      55", " 55    55 ",
            "  5    5  "
        };
        private static final String[] MONSTER_SPRITE = {
            "  1   1  ", "   111   ", "  13331  ", " 3333333 ",
            "333333333", "333242333", "333333333", " 3333333 ",
            "  33333  ", "  3   3  ", " 11   11 "
        };
        private static final String[] TREE_SPRITE = {
            "     11     ", "   1122211   ", "  122222221  ",
            " 12222222221 ", "1222222222221", "1222232222221",
            " 12222222221 ", "  122222221  ", "    4444    ",
            "    4444    "
        };

        private final Monster[] monsters = new Monster[] {
            new Monster(1280, 870), new Monster(1380, 940),
            new Monster(1100, 1020), new Monster(1450, 1110),
            new Monster(900, 850), new Monster(1580, 800),
            new Monster(1700, 1050), new Monster(1050, 700),
            new Monster(2070, 1500, 220, true)
        };

        private int playerX = 1200;
        private int playerY = 900;
        private int level = 1;
        private int experience;
        private int hp = MAX_HP;
        private int potionCount = 2;
        private int moveX;
        private int moveY;
        private int totalKills;
        private int facing = 1;
        private boolean attackHeld;
        private boolean skillRequested;
        private boolean potionRequested;
        private boolean started;
        private boolean victory;
        private boolean bossDefeated;
        private volatile boolean running;
        private volatile Thread loop;
        private long lastAttack;
        private long swingUntil;
        private long skillReadyAt;
        private long skillStartedAt;
        private long lastSave;
        private long messageUntil;
        private String message = "Chao mung hiep khach";

        GameCanvas() {
            setFullScreenMode(true);
            restore();
        }

        synchronized void start() {
            if (running) {
                return;
            }
            running = true;
            loop = new Thread(this);
            loop.start();
        }

        synchronized void pauseGame() {
            save();
            running = false;
        }

        synchronized void stopGame() {
            save();
            running = false;
        }

        public void run() {
            while (running && Thread.currentThread() == loop) {
                updateGame();
                repaint();
                serviceRepaints();
                try {
                    Thread.sleep(70);
                } catch (InterruptedException ignored) {
                }
            }
        }

        protected void paint(Graphics g) {
            int width = getWidth();
            int height = getHeight();
            if (!started) {
                drawTitle(g, width, height);
                return;
            }

            int cameraX = clamp(playerX - width / 2, 0, WORLD_WIDTH - width);
            int cameraY = clamp(playerY - height / 2, 0, WORLD_HEIGHT - height);

            g.setColor(61, 117, 62);
            g.fillRect(0, 0, width, height);
            drawWorld(g, cameraX, cameraY, width, height);
            drawPortal(g, cameraX, cameraY);

            for (int i = 0; i < monsters.length; i++) {
                Monster monster = monsters[i];
                if (monster.alive && (!monster.isBoss || bossUnlocked())) {
                    drawMonster(g, monster.x - cameraX, monster.y - cameraY,
                                monster.hp, monster.maxHp, monster.isDamaged());
                }
            }
            drawPlayer(g, playerX - cameraX, playerY - cameraY);
            drawSkillEffect(g, playerX - cameraX, playerY - cameraY);
            drawHud(g, width, height);
            drawMinimap(g, width);
            if (victory) {
                drawVictory(g, width, height);
                return;
            }
            drawControls(g, width, height);
        }

        private void drawTitle(Graphics g, int width, int height) {
            g.setColor(19, 24, 23);
            g.fillRect(0, 0, width, height);
            g.setColor(242, 200, 116);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_LARGE));
            g.drawString("VLTK2", width / 2, 20,
                         Graphics.TOP | Graphics.HCENTER);
            g.setColor(193, 219, 168);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                   Font.SIZE_SMALL));
            g.drawString("OFFLINE 2D", width / 2, 52,
                         Graphics.TOP | Graphics.HCENTER);

            g.setColor(140, 81, 40);
            g.fillRect(30, 80, width - 60, 120);
            g.setColor(240, 220, 170);
            g.drawString("Nhiem vu:", 42, 90,
                         Graphics.TOP | Graphics.LEFT);
            g.drawString("- Di chuyen bang D-pad", 42, 108,
                         Graphics.TOP | Graphics.LEFT);
            g.drawString("- Dam quai tai vung rung", 42, 124,
                         Graphics.TOP | Graphics.LEFT);
            g.drawString("- Chien thuong de len cap", 42, 140,
                         Graphics.TOP | Graphics.LEFT);
            g.drawString("- Luu tien trinh offline", 42, 156,
                         Graphics.TOP | Graphics.LEFT);

            g.setColor(36, 67, 89);
            g.fillRect(30, height - 80, width - 60, 42);
            g.setColor(255, 250, 220);
            g.drawString("Bam bat ki nut nao de bat dau", width / 2,
                         height - 64, Graphics.TOP | Graphics.HCENTER);
        }

        private void drawVictory(Graphics g, int width, int height) {
            int panelWidth = Math.min(width - 20, 228);
            int panelHeight = 112;
            int x = (width - panelWidth) / 2;
            int y = (height - panelHeight) / 2;
            g.setColor(20, 27, 25);
            g.fillRect(x, y, panelWidth, panelHeight);
            g.setColor(236, 198, 112);
            g.drawRect(x, y, panelWidth, panelHeight);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_LARGE));
            g.drawString("DA HOAN THANH!", width / 2, y + 14,
                         Graphics.TOP | Graphics.HCENTER);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                   Font.SIZE_SMALL));
            g.setColor(224, 218, 191);
            g.drawString("Ban da dep yen vung rung", width / 2, y + 48,
                         Graphics.TOP | Graphics.HCENTER);
            g.drawString("Cham man hinh de choi lai", width / 2, y + 76,
                         Graphics.TOP | Graphics.HCENTER);
        }

        private void startNextRun() {
            victory = false;
            bossDefeated = false;
            totalKills = 0;
            playerX = 1200;
            playerY = 900;
            hp = MAX_HP;
            skillReadyAt = 0;
            skillStartedAt = 0;
            attackHeld = false;
            for (int i = 0; i < monsters.length; i++) {
                Monster monster = monsters[i];
                monster.x = monster.originX;
                monster.y = monster.originY;
                monster.hp = monster.maxHp;
                monster.alive = !monster.isBoss;
                monster.nextHit = 0;
                monster.lastHitAt = 0;
                monster.respawnAt = 0;
            }
            showMessage("Nhiem vu moi bat dau");
            save();
        }

        private void drawWorld(Graphics g, int cameraX, int cameraY,
                               int width, int height) {
            int firstTileX = cameraX / TILE;
            int firstTileY = cameraY / TILE;
            int lastTileX = (cameraX + width) / TILE + 1;
            int lastTileY = (cameraY + height) / TILE + 1;

            for (int ty = firstTileY; ty <= lastTileY; ty++) {
                for (int tx = firstTileX; tx <= lastTileX; tx++) {
                    int screenX = tx * TILE - cameraX;
                    int screenY = ty * TILE - cameraY;
                    int noise = hash(tx, ty) & 0x7fffffff;
                    boolean lake = isLakeTile(tx, ty);
                    boolean shore = !lake && (isLakeTile(tx - 1, ty)
                            || isLakeTile(tx + 1, ty) || isLakeTile(tx, ty - 1)
                            || isLakeTile(tx, ty + 1));
                    boolean road = Math.abs(ty - (25 + tx / 9)) <= 1
                            || Math.abs(tx - 45) <= 1 && ty > 16 && ty < 48;
                    boolean hill = (tx + ty) % 13 == 0 && !lake && !road;

                    if (lake) {
                        g.setColor(34, 103 + (noise & 15), 148);
                    } else if (shore) {
                        g.setColor(133, 130, 78);
                    } else if (road) {
                        g.setColor(137 + (noise & 7), 111 + (noise & 7), 72);
                    } else if (hill) {
                        g.setColor(82, 128, 70);
                    } else {
                        g.setColor(59 + (noise & 7), 112 + (noise & 15), 57);
                    }
                    g.fillRect(screenX, screenY, TILE, TILE);

                    if (lake) {
                        int ripple = (int) (System.currentTimeMillis() / 450
                                            + noise) & 15;
                        g.setColor(99, 177, 191);
                        g.fillRect(screenX + ripple, screenY + 8, 5, 2);
                        g.setColor(61, 132, 164);
                        g.fillRect(screenX + ((ripple + 17) & 27),
                                   screenY + 21, 3, 1);
                    } else if (road) {
                        g.setColor(172, 145, 96);
                        g.fillRect(screenX + (noise & 15), screenY + 20,
                                   3, 2);
                    } else if (shore) {
                        g.setColor(193, 178, 119);
                        g.fillRect(screenX + (noise & 23), screenY + 8, 3, 2);
                    } else if (noise % 37 == 0) {
                        drawTree(g, screenX + 16, screenY + 3);
                    } else if (noise % 11 == 0) {
                        drawGrass(g, screenX, screenY, noise);
                    } else if (noise % 17 == 0) {
                        drawStone(g, screenX, screenY, noise);
                    }
                }
            }
        }

        private boolean isLakeTile(int x, int y) {
            return inLake(x, y, 20, 18, 5, 4)
                    || inLake(x, y, 57, 42, 6, 5)
                    || inLake(x, y, 16, 43, 4, 3);
        }

        private void drawPortal(Graphics g, int cameraX, int cameraY) {
            int x = PORTAL_X - cameraX;
            int y = PORTAL_Y - cameraY;
            g.setColor(24, 91, 124);
            g.fillRect(x - 10, y - 18, 20, 28);
            g.setColor(170, 227, 255);
            g.fillRect(x - 6, y - 14, 12, 20);
            if (victory) {
                g.setColor(255, 220, 120);
                g.fillArc(x - 20, y - 36, 40, 40, 0, 360);
            }
        }

        private void drawTree(Graphics g, int x, int y) {
            drawPixelSprite(g, x, y, TREE_SPRITE, 2, 2, 0, false);
        }

        private void drawGrass(Graphics g, int x, int y, int seed) {
            int tuftX = x + 6 + seed % 18;
            int tuftY = y + 10 + seed % 12;
            g.setColor(34, 83, 43);
            g.drawLine(tuftX, tuftY + 5, tuftX + 2, tuftY);
            g.drawLine(tuftX + 2, tuftY + 5, tuftX + 2, tuftY - 2);
            g.drawLine(tuftX + 3, tuftY + 5, tuftX + 6, tuftY + 1);
            g.setColor(157, 181, 83);
            g.fillRect(tuftX + 2, tuftY - 2, 2, 2);
        }

        private void drawStone(Graphics g, int x, int y, int seed) {
            int stoneX = x + 5 + seed % 17;
            int stoneY = y + 17 + seed % 8;
            g.setColor(54, 66, 55);
            g.fillRect(stoneX, stoneY + 3, 8, 3);
            g.setColor(112, 120, 99);
            g.fillRect(stoneX + 1, stoneY, 6, 4);
            g.setColor(156, 157, 123);
            g.fillRect(stoneX + 2, stoneY, 3, 1);
        }

        private void drawPlayer(Graphics g, int x, int y) {
            int bob = moveX == 0 && moveY == 0 ? 0
                    : ((System.currentTimeMillis() / 130) & 1) == 0 ? 0 : 2;
            g.setColor(28, 36, 31);
            g.fillArc(x - 12, y + 8, 24, 7, 0, 360);
            drawPixelSprite(g, x, y - 17 - bob,
                            PLAYER_SPRITE, 2, 0, 0, false);
            g.setColor(212, 212, 196);
            if (facing > 0) {
                g.drawLine(x + 8, y + 1 - bob, x + 11, y - 16 - bob);
                g.setColor(120, 40, 34);
                g.drawLine(x + 5, y - 3 - bob, x + 11, y - 2 - bob);
            } else {
                g.drawLine(x - 8, y + 1 - bob, x - 11, y - 16 - bob);
                g.setColor(120, 40, 34);
                g.drawLine(x - 5, y - 3 - bob, x - 11, y - 2 - bob);
            }
            if (System.currentTimeMillis() < swingUntil) {
                g.setColor(244, 220, 144);
                g.drawArc(x - 22, y - 28, 44, 44,
                          facing > 0 ? 285 : 75, 100);
                g.setColor(255, 248, 210);
                g.drawArc(x - 18, y - 24, 36, 36,
                          facing > 0 ? 290 : 70, 85);
            }
        }

        private void drawMonster(Graphics g, int x, int y, int monsterHp,
                     int monsterMaxHp, boolean damaged) {
            boolean boss = monsterMaxHp > 36;
            int spriteSize = boss ? 3 : 2;
            g.setColor(28, 36, 31);
            g.fillArc(x - (boss ? 19 : 12), y + 8,
                  boss ? 38 : 24, boss ? 9 : 6, 0, 360);
            if (boss) {
            g.setColor(128, 47, 104);
            g.drawArc(x - 25, y - 29, 50, 50, 0, 360);
            }
            drawPixelSprite(g, x, y + 8 - MONSTER_SPRITE.length * spriteSize,
                MONSTER_SPRITE, spriteSize, boss ? 3 : 1,
                (x + y) & 1, damaged);
            int barWidth = boss ? 34 : 18;
            g.setColor(28, 36, 28);
            g.fillRect(x - barWidth / 2, y - (boss ? 34 : 17), barWidth, 4);
            g.setColor(boss ? 205 : 183, boss ? 83 : 53, boss ? 166 : 45);
            g.fillRect(x - barWidth / 2 + 1, y - (boss ? 33 : 16),
                   Math.max(1, (barWidth - 2) * monsterHp / monsterMaxHp), 2);
        }

        private void drawHud(Graphics g, int width, int height) {
            int panelWidth = Math.min(width < 240 ? width - 12 : width - 78,
                                      184);
            g.setColor(20, 27, 25);
            g.fillRect(5, 5, panelWidth, 66);
            g.setColor(211, 184, 120);
            g.drawRect(5, 5, panelWidth, 66);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN,
                                   Font.SIZE_SMALL));
            g.setColor(245, 225, 177);
            g.drawString("VO LAM 2  |  Cap " + level, 11, 8,
                         Graphics.TOP | Graphics.LEFT);
            g.drawString("Nhiem vu: " + totalKills + "/" + QUEST_GOAL,
                         11, 22, Graphics.TOP | Graphics.LEFT);
            g.setColor(95, 36, 34);
            g.fillRect(11, 38, panelWidth - 22, 7);
            g.setColor(182, 58, 47);
            g.fillRect(11, 38, (panelWidth - 22) * hp / MAX_HP, 7);
            g.setColor(241, 225, 181);
            g.drawString("HP " + hp + "/" + MAX_HP, 11, 46,
                         Graphics.TOP | Graphics.LEFT);
            int needed = level * 40;
            int expWidth = (panelWidth - 22) * experience / needed;
            g.setColor(32, 54, 65);
            g.fillRect(11, 55, panelWidth - 22, 4);
            g.setColor(82, 172, 190);
            g.fillRect(11, 55, expWidth, 4);

            if (System.currentTimeMillis() < messageUntil) {
                int msgWidth = Math.min(width - 24,
                                        g.getFont().stringWidth(message) + 14);
                g.setColor(20, 27, 25);
                g.fillRect((width - msgWidth) / 2, 69, msgWidth, 20);
                g.setColor(245, 225, 177);
                g.drawString(message, width / 2, 72,
                             Graphics.TOP | Graphics.HCENTER);
            }
        }

        private void drawMinimap(Graphics g, int width) {
            if (width < 240) {
                return;
            }
            int size = Math.min(60, width / 4);
            int x = width - size - 6;
            int y = 6;
            int inner = size - 8;
            int mapX = x + 4;
            int mapY = y + 4;

            g.setColor(20, 27, 25);
            g.fillRect(x, y, size, size);
            g.setColor(211, 184, 120);
            g.drawRect(x, y, size, size);
            g.setColor(58, 103, 54);
            g.fillRect(mapX, mapY, inner, inner);

            g.setColor(137, 111, 72);
            int previousX = mapX;
            int previousY = mapY + (25 * inner / (WORLD_HEIGHT / TILE));
            int segment;
            for (segment = 1; segment <= 5; segment++) {
                int worldX = segment * WORLD_WIDTH / 5;
                int tileX = worldX / TILE;
                int tileY = 25 + tileX / 9;
                int nextX = mapX + worldX * inner / WORLD_WIDTH;
                int nextY = mapY + tileY * inner / (WORLD_HEIGHT / TILE);
                g.drawLine(previousX, previousY, nextX, nextY);
                previousX = nextX;
                previousY = nextY;
            }
            int roadX = mapX + 45 * TILE * inner / WORLD_WIDTH;
            g.drawLine(roadX, mapY + 16 * inner / (WORLD_HEIGHT / TILE),
                       roadX, mapY + 48 * inner / (WORLD_HEIGHT / TILE));

            g.setColor(44, 127, 166);
            drawMapLake(g, mapX, mapY, inner, 20, 18, 5, 4);
            drawMapLake(g, mapX, mapY, inner, 57, 42, 6, 5);
            drawMapLake(g, mapX, mapY, inner, 16, 43, 4, 3);

            g.setColor(244, 207, 104);
            int portalX = mapX + PORTAL_X * inner / WORLD_WIDTH;
            int portalY = mapY + PORTAL_Y * inner / WORLD_HEIGHT;
            g.fillRect(portalX - 2, portalY - 2, 5, 5);

            g.setColor(211, 72, 53);
            int index;
            for (index = 0; index < monsters.length; index++) {
                Monster monster = monsters[index];
                if (monster.alive && (!monster.isBoss || bossUnlocked())) {
                    int monsterX = mapX + monster.x * inner / WORLD_WIDTH;
                    int monsterY = mapY + monster.y * inner / WORLD_HEIGHT;
                    if (monster.isBoss) {
                        g.setColor(205, 83, 166);
                        g.fillRect(monsterX - 1, monsterY - 1, 4, 4);
                        g.setColor(211, 72, 53);
                    } else {
                        g.fillRect(monsterX, monsterY, 2, 2);
                    }
                }
            }

            int playerMapX = mapX + playerX * inner / WORLD_WIDTH;
            int playerMapY = mapY + playerY * inner / WORLD_HEIGHT;
            g.setColor(239, 246, 220);
            g.fillRect(playerMapX - 2, playerMapY - 2, 5, 5);
            g.setColor(45, 49, 39);
            g.fillRect(playerMapX - 1, playerMapY - 1, 3, 3);
        }

        private void drawMapLake(Graphics g, int mapX, int mapY, int size,
                                 int centerTileX, int centerTileY,
                                 int radiusTileX, int radiusTileY) {
            int centerX = mapX + centerTileX * TILE * size / WORLD_WIDTH;
            int centerY = mapY + centerTileY * TILE * size / WORLD_HEIGHT;
            int radiusX = Math.max(2, radiusTileX * TILE * size / WORLD_WIDTH);
            int radiusY = Math.max(2, radiusTileY * TILE * size / WORLD_HEIGHT);
            g.fillArc(centerX - radiusX, centerY - radiusY,
                      radiusX * 2, radiusY * 2, 0, 360);
        }

        private void drawControls(Graphics g, int width, int height) {
            int bottom = height - 15;
            drawButton(g, 35, bottom - 38, "<");
            drawButton(g, 111, bottom - 38, ">");
            drawButton(g, 73, bottom - 76, "^");
            drawButton(g, 73, bottom, "v");
            drawPotionButton(g, 73, bottom - 38);

            int attackX = width - 49;
            int attackY = height - 52;
            g.setColor(attackHeld ? 145 : 91, 42, 37);
            g.fillArc(attackX - 29, attackY - 29, 58, 58, 0, 360);
            g.setColor(225, 190, 124);
            g.drawArc(attackX - 29, attackY - 29, 58, 58, 0, 360);
            g.setColor(244, 229, 194);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_SMALL));
            g.drawString("ATK", attackX, attackY - 5,
                         Graphics.TOP | Graphics.HCENTER);

            int skillX = width - 49;
            int skillY = height - 124;
            long now = System.currentTimeMillis();
            boolean ready = now >= skillReadyAt;
            g.setColor(ready ? 45 : 46, ready ? 103 : 57, ready ? 119 : 68);
            g.fillArc(skillX - 23, skillY - 23, 46, 46, 0, 360);
            g.setColor(ready ? 184 : 106, ready ? 220 : 129,
                       ready ? 224 : 139);
            g.drawArc(skillX - 23, skillY - 23, 46, 46, 0, 360);
            g.setColor(238, 242, 221);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_SMALL));
            g.drawString("SKL", skillX, skillY - 7,
                         Graphics.TOP | Graphics.HCENTER);
            if (!ready) {
                int seconds = (int) ((skillReadyAt - now + 999) / 1000);
                g.drawString("" + seconds, skillX, skillY + 6,
                             Graphics.TOP | Graphics.HCENTER);
            }
        }

        private void drawSkillEffect(Graphics g, int x, int y) {
            long elapsed = System.currentTimeMillis() - skillStartedAt;
            if (skillStartedAt == 0 || elapsed < 0 || elapsed >= 420) {
                return;
            }
            int diameter = 44 + (int) (elapsed * 150 / 420);
            g.setColor(90, 218, 233);
            g.drawArc(x - diameter / 2, y - diameter / 2,
                      diameter, diameter, 0, 360);
            g.setColor(237, 248, 208);
            g.drawArc(x - diameter / 2 + 4, y - diameter / 2 + 4,
                      diameter - 8, diameter - 8, 0, 360);
        }

        private void drawPotionButton(Graphics g, int x, int y) {
            g.setColor(41, 69, 49);
            g.fillArc(x - 13, y - 13, 26, 26, 0, 360);
            g.setColor(169, 203, 136);
            g.drawArc(x - 13, y - 13, 26, 26, 0, 360);
            g.setColor(222, 83, 72);
            g.fillRect(x - 4, y - 4, 8, 10);
            g.setColor(245, 220, 156);
            g.fillRect(x - 2, y - 8, 4, 4);
            g.setColor(242, 232, 197);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_SMALL));
            g.drawString("" + potionCount, x, y + 6,
                         Graphics.TOP | Graphics.HCENTER);
        }

        private void drawButton(Graphics g, int x, int y, String label) {
            g.setColor(31, 40, 36);
            g.fillArc(x - 17, y - 17, 34, 34, 0, 360);
            g.setColor(181, 164, 126);
            g.drawArc(x - 17, y - 17, 34, 34, 0, 360);
            g.setColor(238, 226, 192);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,
                                   Font.SIZE_MEDIUM));
            g.drawString(label, x, y - 8, Graphics.TOP | Graphics.HCENTER);
        }

        private void updateGame() {
            if (!started || victory) {
                return;
            }
            long now = System.currentTimeMillis();
            int nextX = playerX + moveX * 3;
            int nextY = playerY + moveY * 3;
            if (!isBlocked(nextX, playerY)) {
                playerX = clamp(nextX, 12, WORLD_WIDTH - 12);
            }
            if (!isBlocked(playerX, nextY)) {
                playerY = clamp(nextY, 12, WORLD_HEIGHT - 12);
            }

            if (moveX != 0 || moveY != 0) {
                facing = moveX < 0 ? -1 : 1;
            }

            if (skillRequested) {
                skillRequested = false;
                useSkill(now);
            }
            if (potionRequested) {
                potionRequested = false;
                usePotion();
            }

            for (int i = 0; i < monsters.length; i++) {
                Monster monster = monsters[i];
                if (monster.isBoss && !bossUnlocked()) {
                    continue;
                }
                if (!monster.alive) {
                    if (monster.isBoss && bossDefeated) {
                        continue;
                    }
                    if (now >= monster.respawnAt) {
                        monster.alive = true;
                        monster.hp = monster.maxHp;
                    }
                    continue;
                }

                int dx = playerX - monster.x;
                int dy = playerY - monster.y;
                int distance = dx * dx + dy * dy;
                if (distance < 210 * 210 && distance > 30 * 30) {
                    int stepX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
                    int stepY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
                    monster.x += stepX;
                    monster.y += stepY;
                } else if (distance <= 30 * 30 && now >= monster.nextHit) {
                    hp -= monster.isBoss ? 8 + level / 2 : 3 + level / 3;
                    monster.nextHit = now + (monster.isBoss ? 850 : 1100);
                    if (hp <= 0) {
                        hp = MAX_HP;
                        playerX = 1200;
                        playerY = 900;
                        showMessage("Ban da hoi sinh tai diem xuat phat");
                        save();
                    }
                }
            }

            if (attackHeld && now - lastAttack >= 520) {
                attack();
                lastAttack = now;
            }
            if (now - lastSave >= 5000) {
                save();
                lastSave = now;
            }

            if (bossDefeated && isNearPortal(playerX, playerY)) {
                victory = true;
                showMessage("Hoan thanh nhiem vu!");
                save();
            }
        }

        private void attack() {
            swingUntil = System.currentTimeMillis() + 180;
            Monster target = null;
            int bestDistance = 64 * 64;
            for (int i = 0; i < monsters.length; i++) {
                Monster monster = monsters[i];
                if (!monster.alive || monster.isBoss && !bossUnlocked()) {
                    continue;
                }
                int dx = playerX - monster.x;
                int dy = playerY - monster.y;
                int distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    target = monster;
                    bestDistance = distance;
                }
            }
            if (target == null) {
                showMessage("Khong co quai vat o gan");
                return;
            }

            damageMonster(target, 9 + level * 2);
        }

        private void useSkill(long now) {
            if (now < skillReadyAt) {
                showMessage("Ky nang hoi trong "
                        + ((skillReadyAt - now + 999) / 1000) + " giay");
                return;
            }
            skillReadyAt = now + SKILL_COOLDOWN;
            skillStartedAt = now;
            int hitCount = 0;
            int radius = 112;
            for (int i = 0; i < monsters.length; i++) {
                Monster monster = monsters[i];
                if (!monster.alive || monster.isBoss && !bossUnlocked()) {
                    continue;
                }
                int dx = playerX - monster.x;
                int dy = playerY - monster.y;
                if (dx * dx + dy * dy <= radius * radius) {
                    damageMonster(monster, 24 + level * 4);
                    hitCount++;
                }
            }
            if (hitCount == 0) {
                showMessage("Khong co quai trong tam ky nang");
            } else {
                showMessage("Ky nang trung " + hitCount + " muc tieu");
            }
        }

        private void damageMonster(Monster monster, int damage) {
            monster.lastHitAt = System.currentTimeMillis();
            monster.hp -= damage;
            if (monster.hp > 0) {
                return;
            }
            monster.alive = false;
            if (monster.isBoss) {
                bossDefeated = true;
                experience += 100 + level * 10;
                potionCount = Math.min(9, potionCount + 2);
                showMessage("Quan chu da bi tieu diet!");
                while (experience >= level * 40) {
                    experience -= level * 40;
                    level++;
                    hp = MAX_HP;
                }
                save();
                return;
            }
            monster.respawnAt = System.currentTimeMillis() + 9000;
            totalKills++;
            experience += 12 + level * 3;
            if (totalKills % 3 == 0 && potionCount < 9) {
                potionCount++;
                showMessage("Nhan duoc binh hoi mau");
            }
            showMessage("Ha quai vat! " + totalKills + "/" + QUEST_GOAL);
            while (experience >= level * 40) {
                experience -= level * 40;
                level++;
                hp = MAX_HP;
                showMessage("Len cap! Cap " + level);
            }
            if (totalKills == QUEST_GOAL) {
                showMessage("Quan chu xuat hien tai cong!");
            }
            save();
        }

        private void showMessage(String text) {
            message = text;
            messageUntil = System.currentTimeMillis() + 1800;
        }

        private void usePotion() {
            if (potionCount <= 0) {
                showMessage("Da het binh hoi mau");
                return;
            }
            if (hp >= MAX_HP) {
                showMessage("Sinh luc dang day");
                return;
            }
            int oldHp = hp;
            hp = Math.min(MAX_HP, hp + 45);
            potionCount--;
            showMessage("Hoi phuc " + (hp - oldHp) + " sinh luc");
            save();
        }

        private boolean bossUnlocked() {
            return totalKills >= QUEST_GOAL && !bossDefeated;
        }

        protected void keyPressed(int keyCode) {
            if (victory) {
                startNextRun();
                return;
            }
            if (!started) {
                started = true;
                showMessage("Bat dau chien dau");
                return;
            }

            int action = getGameAction(keyCode);
            if (action == LEFT) {
                moveX = -1;
            } else if (action == RIGHT) {
                moveX = 1;
            } else if (action == UP) {
                moveY = -1;
            } else if (action == DOWN) {
                moveY = 1;
            } else if (action == FIRE) {
                attackHeld = true;
            }
        }

        protected void keyReleased(int keyCode) {
            int action = getGameAction(keyCode);
            if (action == LEFT && moveX < 0 || action == RIGHT && moveX > 0) {
                moveX = 0;
            } else if (action == UP && moveY < 0 || action == DOWN && moveY > 0) {
                moveY = 0;
            }
            if (action == FIRE) {
                attackHeld = false;
            }
        }

        protected void pointerPressed(int x, int y) {
            if (victory) {
                startNextRun();
                return;
            }
            if (!started) {
                started = true;
                showMessage("Bat dau chien dau");
                return;
            }
            updatePointer(x, y, true);
        }

        protected void pointerDragged(int x, int y) {
            updatePointer(x, y, false);
        }

        protected void pointerReleased(int x, int y) {
            moveX = 0;
            moveY = 0;
            attackHeld = false;
        }

        private void updatePointer(int x, int y, boolean allowSkill) {
            int bottom = getHeight() - 15;
            moveX = 0;
            moveY = 0;
            if (allowSkill) {
                potionRequested = false;
                int potionDx = x - 73;
                int potionDy = y - (bottom - 38);
                if (potionDx * potionDx + potionDy * potionDy <= 17 * 17) {
                    potionRequested = true;
                    attackHeld = false;
                    skillRequested = false;
                    return;
                }
            }
            if (x < 70 && y > bottom - 58 && y < bottom - 18) {
                moveX = -1;
            } else if (x > 92 && x < 148 && y > bottom - 58
                    && y < bottom - 18) {
                moveX = 1;
            } else if (x >= 54 && x <= 92 && y > bottom - 96
                    && y <= bottom - 58) {
                moveY = -1;
            } else if (x >= 54 && x <= 92 && y >= bottom - 18
                    && y < bottom + 20) {
                moveY = 1;
            }
            int dx = x - (getWidth() - 49);
            int dy = y - (getHeight() - 52);
            attackHeld = dx * dx + dy * dy <= 38 * 38;
            if (allowSkill) {
                int skillY = getHeight() - 124;
                int skillDy = y - skillY;
                skillRequested = dx * dx + skillDy * skillDy <= 27 * 27;
            }
        }

        private boolean isBlocked(int x, int y) {
            int tileX = x / TILE;
            int tileY = y / TILE;
            return inLake(tileX, tileY, 20, 18, 5, 4)
                    || inLake(tileX, tileY, 57, 42, 6, 5)
                    || inLake(tileX, tileY, 16, 43, 4, 3);
        }

        private boolean isNearPortal(int x, int y) {
            int dx = x - PORTAL_X;
            int dy = y - PORTAL_Y;
            return dx * dx + dy * dy < 30 * 30;
        }

        private boolean inLake(int x, int y, int centerX, int centerY,
                               int radiusX, int radiusY) {
            int dx = x - centerX;
            int dy = y - centerY;
            return dx * dx * radiusY * radiusY
                    + dy * dy * radiusX * radiusX
                    <= radiusX * radiusX * radiusY * radiusY;
        }

        private int hash(int x, int y) {
            int value = x * 374761393 + y * 668265263;
            value = (value ^ (value >>> 13)) * 1274126177;
            return value ^ (value >>> 16) & 0x7fffffff;
        }

        private int clamp(int value, int min, int max) {
            return value < min ? min : (value > max ? max : value);
        }

        private void drawPixelSprite(Graphics g, int centerX, int topY,
                                     String[] sprite, int pixelSize,
                                     int palette, int variant,
                                     boolean damaged) {
            int spriteWidth = 0;
            for (int row = 0; row < sprite.length; row++) {
                spriteWidth = Math.max(spriteWidth, sprite[row].length());
            }
            int leftX = centerX - spriteWidth * pixelSize / 2;
            for (int row = 0; row < sprite.length; row++) {
                String pixels = sprite[row];
                int rowX = leftX + (spriteWidth - pixels.length())
                        * pixelSize / 2;
                int column = 0;
                while (column < pixels.length()) {
                    char color = pixels.charAt(column);
                    if (color == ' ') {
                        column++;
                        continue;
                    }
                    int runEnd = column + 1;
                    while (runEnd < pixels.length()
                            && pixels.charAt(runEnd) == color) {
                        runEnd++;
                    }
                    setSpriteColor(g, color, palette, variant, damaged);
                    g.fillRect(rowX + column * pixelSize,
                               topY + row * pixelSize,
                               (runEnd - column) * pixelSize, pixelSize);
                    column = runEnd;
                }
            }
        }

        private void setSpriteColor(Graphics g, char color, int palette,
                                    int variant, boolean damaged) {
            if (damaged && color == '3') {
                g.setColor(247, 231, 203);
            } else if (color == '0' || color == '1') {
                g.setColor(31, 39, 35);
            } else if (color == '2') {
                if (palette == 2) {
                    g.setColor(28, 78 + variant * 7, 43);
                } else if (palette == 1) {
                    g.setColor(229, 188, 73);
                } else {
                    g.setColor(225, 174, 124);
                }
            } else if (color == '3') {
                if (palette == 2) {
                    g.setColor(54, 112 + variant * 5, 52);
                } else if (palette == 1) {
                    g.setColor(103, 138, 67);
                } else {
                    g.setColor(169, 57, 47);
                }
            } else if (color == '4') {
                if (palette == 2) {
                    g.setColor(86, 57, 37);
                } else {
                    g.setColor(212, 165, 79);
                }
            } else if (color == '5') {
                g.setColor(45, 52, 57);
            } else if (color == '6') {
                g.setColor(218, 180, 112);
            } else if (color == '7') {
                g.setColor(118, 48, 37);
            } else if (color == '8') {
                g.setColor(75, 112, 60);
            } else if (color == '9') {
                g.setColor(119, 159, 71);
            }
        }

        private void restore() {
            RecordStore store = null;
            try {
                store = RecordStore.openRecordStore(STORE_NAME, false);
                if (store.getNumRecords() > 0) {
                    byte[] data = store.getRecord(1);
                    DataInputStream input = new DataInputStream(
                            new ByteArrayInputStream(data));
                    playerX = clamp(input.readInt(), 12, WORLD_WIDTH - 12);
                    playerY = clamp(input.readInt(), 12, WORLD_HEIGHT - 12);
                    level = Math.max(1, input.readInt());
                    experience = Math.max(0, input.readInt());
                    hp = clamp(input.readInt(), 1, MAX_HP);
                    totalKills = Math.max(0, input.readInt());
                    if (input.available() > 0) {
                        victory = input.readBoolean();
                    }
                    if (input.available() > 0) {
                        bossDefeated = input.readBoolean();
                    }
                    if (input.available() >= 4) {
                        potionCount = clamp(input.readInt(), 0, 9);
                    }
                    if (victory) {
                        started = true;
                    }
                    while (experience >= level * 40) {
                        experience -= level * 40;
                        level++;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (store != null) {
                    try {
                        store.closeRecordStore();
                    } catch (RecordStoreException ignored) {
                    }
                }
            }
        }

        private void save() {
            RecordStore store = null;
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeInt(playerX);
                output.writeInt(playerY);
                output.writeInt(level);
                output.writeInt(experience);
                output.writeInt(hp);
                output.writeInt(totalKills);
                output.writeBoolean(victory);
                output.writeBoolean(bossDefeated);
                output.writeInt(potionCount);
                output.flush();

                store = RecordStore.openRecordStore(STORE_NAME, true);
                byte[] data = bytes.toByteArray();
                if (store.getNumRecords() == 0) {
                    store.addRecord(data, 0, data.length);
                } else {
                    store.setRecord(1, data, 0, data.length);
                }
            } catch (RecordStoreException ignored) {
            } catch (IOException ignored) {
            } finally {
                if (store != null) {
                    try {
                        store.closeRecordStore();
                    } catch (RecordStoreException ignored) {
                    }
                }
            }
        }
    }

    private static final class Monster {
        final int maxHp;
        final boolean isBoss;
        final int originX;
        final int originY;
        int x;
        int y;
        int hp;
        long nextHit;
        long lastHitAt;
        long respawnAt;
        boolean alive = true;

        Monster(int x, int y) {
            this(x, y, 36, false);
        }

        Monster(int x, int y, int maxHp, boolean isBoss) {
            this.originX = x;
            this.originY = y;
            this.x = x;
            this.y = y;
            this.maxHp = maxHp;
            this.hp = maxHp;
            this.isBoss = isBoss;
            this.alive = !isBoss;
        }

        boolean isDamaged() {
            return System.currentTimeMillis() - lastHitAt < 130;
        }
    }
}