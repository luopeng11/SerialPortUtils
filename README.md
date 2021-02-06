# SerialPortUtils
Android 串口通信（ttl&amp;232$485&amp;usb）DEMO  解决拼包问题
Android串口数据传输过大或读取速率较慢  会导致写入时 缓冲区覆盖重写导致数据丢失，所以读取时必须保证读取速度大于写入速度，执行耗时操作开子线程进行；
此dmo可以解决分包问题，依据帧尾 帧头将分散数据拼接成一帧完整数据；
通信不频繁情况下，一般不会出现粘包问题；
要解决粘包问题，数据协议每一帧须包含帧头、帧尾、帧长度。