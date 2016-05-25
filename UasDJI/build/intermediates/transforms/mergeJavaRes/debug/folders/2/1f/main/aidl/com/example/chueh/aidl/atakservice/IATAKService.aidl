package com.example.chueh.aidl.atakservice;

 interface IATAKService
 {
   int add(int num1, int num2);
   ParcelFileDescriptor startDJIVid();
 }