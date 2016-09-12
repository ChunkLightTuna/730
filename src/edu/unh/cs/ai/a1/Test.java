package test;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class Test {

    final static Semaphore ss = new Semaphore(0);


    static class TT implements Runnable {

        @Override
        public void run() {
            try {
                Socket t = new Socket("localhost", 47111);
                InputStream is = t.getInputStream();
                for (;;) {
                    is.read();
                }

            } catch (Throwable t) {
                System.err.println(Thread.currentThread().getName() + " : abort");
                t.printStackTrace();
                System.exit(2);
            }

        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {

            Thread t = new Thread() {
                public void run() {
                    try {
                        ArrayList<Socket> sockets = new ArrayList<Socket>(50000);
                        ServerSocket s = new ServerSocket(47111,1500);
                        ss.release();

                        for (;;) {
                            Socket t = s.accept();
                            sockets.add(t);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);

                    }
                }
            };


            t.start();
            ss.acquire();


            for (int i = 0; i < 30000; i++) {

                Thread tt = new Thread(new TT(), "T" + i);
                tt.setDaemon(true);
                tt.start();
                System.out.println(tt.getName());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return;
                }
            }

            for (;;) {
                System.out.println();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}