/*
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
 * Copyright (C) 2013 The OmniROM Project
 */
/*
 * This file is part of OpenDelta.
 *
 * OpenDelta is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenDelta is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenDelta. If not, see <http://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include "zipadjust.h"
#include "delta.h"

JNIEXPORT jint JNICALL Java_eu_chainfire_opendelta_Native_zipadjust(JNIEnv * env, jobject clazz, jstring jFilenameIn, jstring jFilenameOut, jint decompress) {
	const char* filenameIn = (*env)->GetStringUTFChars(env, jFilenameIn, 0);
	const char* filenameOut = (*env)->GetStringUTFChars(env, jFilenameOut, 0);

	jint ret = zipadjust((char*)filenameIn, (char*)filenameOut, decompress);

	(*env)->ReleaseStringUTFChars(env, jFilenameOut, filenameOut);
	(*env)->ReleaseStringUTFChars(env, jFilenameIn, filenameIn);

	return ret;
}

JNIEXPORT jint JNICALL Java_eu_chainfire_opendelta_Native_dedelta(JNIEnv * env, jobject clazz, jstring jFilenameSource, jstring jFilenameDelta, jstring jFilenameOut) {
	const char* filenameSource = (*env)->GetStringUTFChars(env, jFilenameSource, 0);
	const char* filenameDelta = (*env)->GetStringUTFChars(env, jFilenameDelta, 0);
	const char* filenameOut = (*env)->GetStringUTFChars(env, jFilenameOut, 0);

	jint ret = dedelta((char*)filenameSource, (char*)filenameDelta, (char*)filenameOut);

	(*env)->ReleaseStringUTFChars(env, jFilenameOut, filenameOut);
	(*env)->ReleaseStringUTFChars(env, jFilenameDelta, filenameDelta);
	(*env)->ReleaseStringUTFChars(env, jFilenameSource, filenameSource);

	return ret;
}
