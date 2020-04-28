const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// 感染された方の位置履歴を即時感染エリアに追加する。
exports.userTraceListener = functions
    .region('asia-northeast1')
    .firestore
    .document('users/{userid}/trace-infos/{traceid}')
    .onCreate(async (snap, context) => {
        const statusRef = admin.firestore().collection('users')
                              .doc(context.params.userid)
                              .collection('infos').doc("status");

        const status = await statusRef.get();
        if (status.data().infection_flag === 1) {
            const inflectionRef = admin.firestore().collection('corona-infos')
                                      .doc(snap.data()
                                      .locationcode)
                                      .collection('users')
                                      .doc(context.params.userid);

            return inflectionRef.set({
                timestamp: snap.data().timestamp,
                userid: context.params.userid,
            });
        }

        return null;
});

// 感染された方の位置履歴を即時感染エリアから削除する。
exports.userTraceListener = functions
    .region('asia-northeast1')
    .firestore
    .document('users/{userid}/trace-infos/{traceid}')
    .onDelete(async (snap, context) => {
        const inflectionRef = admin.firestore().collection('corona-infos')
                                    .doc(snap.data().locationcode)
                                    .collection('users')
                                    .doc(context.params.userid);

        return inflectionRef.delete({
            timestamp: snap.data().timestamp,
            userid: context.params.userid,
        });
});


// 即時感染エリア作成時、感染密度を更新する。
exports.addInflectionListener = functions
    .region('asia-northeast1')
    .firestore
    .document('corona-infos/{locationcode}/users/{userid}')
    .onCreate( async (snap, context) => {
        const inflectionRef = admin.firestore().collection('corona-infos')
                                  .doc(context.params.locationcode);

        return inflectionRef.set({
                  density: admin.firestore.FieldValue.increment(1)}, {merge: true});
});

// 即時感染エリア削除時、感染密度を更新する。
exports.delInflectionListener = functions
    .region('asia-northeast1')
    .firestore
    .document('corona-infos/{locationcode}/users/{userid}')
    .onDelete( async (snap, context) => {
        const inflectionRef = admin.firestore().collection('corona-infos')
                                  .doc(context.params.locationcode);

        return inflectionRef.set({
              density: admin.firestore.FieldValue.increment(-1)}, {merge: true});
});

// ユーザーの感染報告された場合、位置履歴を感染エリアに追加する。
// ユーザーの感染報告取り消された場合、位置履歴を感染エリアに削除する。
exports.userInflectionReportListener = functions
    .region('asia-northeast1')
    .firestore
    .document('users/{userid}/infos/status')
    .onUpdate(async (change, context) => {

        const coronaSettingInfoRef = admin.firestore().collection('corona-infos').doc('setting-info');
        const coronaSettingInfo = await coronaSettingInfoRef.get();

        if (change.before.data().infection_flag === 0) {
            if (change.after.data().infection_flag === 1) {
                console.info("create inflection infomation", "==>", context.params.userid);

                const traceInfosRef = admin.firestore().collection('users')
                                          .doc(context.params.userid).collection('trace-infos');
                const traceInfos = await traceInfosRef.get();

                //console.log("traceInfos", '=>', traceInfos);
                let batch = admin.firestore().batch();
                traceInfos.docs.forEach(doc => {
                    //console.log(doc.id, '=>', doc.data());

                    const inflectionRef = admin.firestore().collection('corona-infos')
                                              .doc(doc.data().locationcode)
                                              .collection('users')
                                              .doc(context.params.userid);

                    batch.set(inflectionRef, {
                        timestamp: doc.data().timestamp,
                        userid: context.params.userid,
                    });
                });
                await(batch.commit());
            }
        }
        else if (change.before.data().infection_flag === 1) {
            if (change.after.data().infection_flag === 0) {
                console.info("delete inflection infomation", "==>", context.params.userid);

                await deleteUserInflectInfos(admin.firestore(), context.params.userid, 10000);
            }
        }

        const coronaInfoRef = admin.firestore().collection('corona-infos').doc("info");
        return coronaInfoRef.set(
                    {timestamp: admin.firestore.FieldValue.serverTimestamp()}, 
                    {merge: true});
});

function deleteUserInflectInfos(db, userid, batchSize) {
    let collectionRef = db.collectionGroup("users");
    //let query = collectionRef.orderBy(admin.firestore.FieldPath.documentId())
    //                        .limit(batchSize)
    //                        .where(userid, '==', userid);
    let query = collectionRef.orderBy(admin.firestore.FieldPath.documentId())
                                .limit(batchSize);

    return new Promise((resolve, reject) => {
      deleteInflectionUser(db, query, userid, batchSize, resolve, reject);
    });
  }

function deleteInflectionUser(db, query, userid, batchSize, resolve, reject) {
  query.get()
    .then((snapshot) => {
      // When there are no documents left, we are done
      if (snapshot.size == 0) {
        return 0;
      }

      // Delete documents in a batch
      let batch = db.batch();
      snapshot.docs.forEach((doc) => {
        if (doc.data().userid === userid) {
          batch.delete(doc.ref);
        }
      });

      return batch.commit().then(() => {
        return snapshot.size;
      });
    }).then((numDeleted) => {
      if (numDeleted === 0) {
        resolve();
        return;
      }

      // Recurse on the next process tick, to avoid
      // exploding the stack.
      process.nextTick(() => {
        deleteInflectionUser(db, query, userid, batchSize, resolve, reject);
      });
    })
    .catch(reject);
}

// 共通タスク
function deleteQueryBatch(db, query, batchSize, resolve, reject) {
  query.get()
    .then((snapshot) => {
      // When there are no documents left, we are done
      if (snapshot.size == 0) {
        return 0;
      }

      // Delete documents in a batch
      let batch = db.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });

      return batch.commit().then(() => {
        return snapshot.size;
      });
    }).then((numDeleted) => {
      if (numDeleted === 0) {
        resolve();
        return;
      }

      // Recurse on the next process tick, to avoid
      // exploding the stack.
      process.nextTick(() => {
        deleteQueryBatch(db, query, batchSize, resolve, reject);
      });
    })
    .catch(reject);
}