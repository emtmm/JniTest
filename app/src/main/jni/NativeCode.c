#include <string.h>
#include <jni.h>
/*
 * Class:     com_inde_ndksupport_app_MainActivity
 * Method:    getStringFromNative
 * Signature: ()Ljava/lang/String;
 */

jstring Java_com_emtmm_jnitest_MainActivity_getStringFromNative
  (JNIEnv *env , jobject obj)
  {

        return (*env)->NewStringUTF(env,"Hello From JNI");



  }


