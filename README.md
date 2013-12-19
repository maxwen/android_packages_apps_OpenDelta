About
-----

**OpenDelta** was written to provide automatic OTA updates for 
**The OmniROM Project**'s nightly builds, making use of deltas when possible,
to reduce the size of the download.

There's no reason you couldn't use it for weeklies or monthlies or milestones as 
well though!


License
-------

**OpenDelta** is licensed under the terms of the *GNU General Public License,
version 3.0*. See the *COPYING* file for the full license text.


How
---

**OpenDelta** uses binary differentials (VCDIFF, RFC 3284) between the previous
and the current release, courtesy of **xdelta3** (<http://xdelta.org/>).

Usually, OTA ZIP files created by Android builds are compressed. Diffs between
compressed OTA ZIPs are not ideal - they are significantly larger than the diff
between two uncompressed OTA ZIPs would be. So before we create the diff we 
decompress the contents of the OTA ZIPs.

The whole-file signature of the OTA ZIP is broken (and removed) by this process, 
and so we also re-sign the decompressed ZIPs with the same keys used to build 
Android. We create a second diff between the unsigned and re-signed ZIP file,
so if needed the client can re-create a properly signed ZIP file.

The produced delta files are pushed to the public download server, and the 
current build is saved to a private location to serve as input for the next
differential run. 

It is important to note that the differential files are named after the *input*
file, not after the *output* file. Initially this may seem a bit confusing when
working with these files, but this way the client doesn't need to know any 
information about future builds when looking for updates, and no server logic
is needed at all - just a public download location - as the delta filename can
be reconstructed from getprop's on the device.

The Android client periodically checks in with the download server and 
retrieves the *.delta* file for its current build. After parsing it, it knows
the name for the next build as well, and then the one after that, etc. So 
if you don't update for a number of builds, it can still reconstruct the latest
build by chaining the deltas. It will check each delta if we already have 
intermediate files present - perhaps we already performed the work for the last
build but never flashed it, for example. Based on all this information it will
decide to either reconstruct the final flashable ZIP, or just download the
latest full OTA and flash that. 


Compatibility
-------------

**OpenDelta** is developed for use with **TWRP**, and uses scripting to 
accomplish its tasks. Other recoveries with *full* **OpenRecoveryScript** 
*may* work as well, but are not tested against.

**CWM** is not officially supported by **OpenDelta**, though if not 
operating in **secure mode**, a script that *may* work with 
*community-built* **CWM** versions is generated as well. *Official*
**CWM** builds (acquired from the CWM website or installed by
*ROM Manager*) are **not supported** as they disable scripting 
capabilities. Even *if* this script works with your build, you may encounter 
it using the wrong storage paths, failing verification, producing various 
errors, etc.


Security
--------

The OTA ZIPs that **OpenDelta** downloads or re-generates are stored on
either internal or external storage. These locations are not secure, as any
malicious app can write to these locations, and with some careful timing
place its own update to be flashed instead of our update, thus gaining 
full system access.

Additionally, **OpenDelta** conveniently flashes ZIPs located in the
**FlashAfterUpdate** subfolder of its storage. A malicious app could add
its own ZIPs to the list, thus gaining full system access.

**OpenDelta** has the capability to re-generate OTA ZIPs fully signed with
your private keys (without knowing them). Assuming you aren't using a set
of publicly known keys to sign your ZIPs (ouch!), this can be used to make 
your update secure.

Chances are that the recovery you are using does not have your public key
built-in for whole-file verification purposes, and thus verification would fail. 
This is why **OpenDelta** also provides the capability to inject your public 
key into the recovery. This public key is provided to the recovery through
the /cache partition, which non-privileged apps cannot write to.

These features combined allows the recovery to verify the update signature
securely without the chance of a malicious app hijacking either the update
or the keys. However, this feature **only** works with **TWRP**, and the
signatures will not be checked by non-*OpenRecoveryScript* recoveries. It
also leaves open the **FlashAfterUpdate** hole, as ZIPs stored there by
the user will (likely) not be signed with the same keys as your update,
and thus their origins cannot be verified.

If **OpenDelta** is configured with all the needed parts to re-generate the
OTA ZIPs fully signed, and verify the signatures in recovery, then **secure
mode** becomes available (whether or not it is enabled by default is also
a configuration switch). In **secure mode**, the public key injection and 
signature verification features are enabled, additional ZIPs from the 
**FlashAfterUpdate** subfolder will **not** be flashed, and the 
**CWM**-compatibile script will **not** be generated. Unless your recovery
is compromised, this should provide for fully secure flashing.

Of course, the user has the option to enable or disable this feature from the
actionbar menu.


Bad builds
----------

As **OpenDelta** depends on an unbroken chain of deltas, you can't just remove
the files of a bad/dangerous/etc build. If you want to prevent the client from
producing and flashing such a build, rename the relevant *.delta* file to
*.delta_revoked*.

We'd still have a problem if you want to produce a replacement build, or for
some reason have several different builds with the same name, and this is 
breaking the chain of deltas. The solution for this is to edit the relevant
*.delta* file, and setting the *size* of the *update* file to a value larger
than the *size_official* of the *out* file. This will trigger the client to
download the full-size compressed OTA ZIP instead.


Server-side
-----------

To create the delta files on the server, you need several things, some of 
which can be found in the *server* directory. The main thing is the 
*opendelta.sh* script. It contains a configuration section which you can edit 
with the correct file locations on your own system. Quite likely you will need
to create a wrapper script that pulls in your previous release and your 
current release, and pushes out the created delta files.

The script depends on *xdelta3*, *zipadjust* and *minsignapk*.

For the builds on your server, make a *copy* of the *jni* directory - do **not**
compile inside *jni* because you may mess up the build of *libopendelta*.  

*xdelta3* can be built in (the copy of) *jni/xdelta3-3.0.7* by calling *./configure*
and *make*.

*zipadjust* can be built in (the copy of) *jni* by calling:

gcc -o zipadjust zipadjust.c zipadjust_run.c -lz

*dedelta* (not used by the script, but maybe helpful when debugging) can be built
in (the copy of) *jni* by calling:

gcc -o dedelta xdelta3-3.0.7/xdelta3.c delta.c delta_run.c

*minsignapk* Java source is in the *server* directory, as well as a prebuilt
*minsignapk.jar* file that should work on most systems


Eclipse
-------

For debugging purposes you may wish to build in Eclipse instead of an Android
tree, for test-speed benefits. The native part of **OpenDelta** is also NDK
buildable.

You may need to enable the app to show up in the launcher ("System Updates")
by editing *AndroidManifest*.

The APK needs privileged system permissions, and thus needs to placed in
*/system/priv-app*. If you're testing on a build that includes **OpenDelta**
already, remove it from that location and reboot before continuing. If you
install the APK through Eclipse it'll end up in */data/app*, but will not be
granted the right permissions. Move that APK to */system/priv-app* and reboot.
Now increase the *versionCode* in *AndroidManifest* to a larger number, and
your Eclipse-installed builds will magically run with the right permissions
granted every time. You could use *pm grant* but you'd have to do that after
every install.
  
Aside from inside the APK, you also need to place the produced *libopendelta.so*
for your architecture in */system/lib*. If you're actually working on the
native library this gets annoying fast, symlinking that location to the library
location from the APK can save you a lot of headache. 


-EOF-