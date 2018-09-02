package test;

class MyThread extends Thread{
	public void run() {
		System.out.format("%d\t%d\n", getId(), getPriority());
	}
}