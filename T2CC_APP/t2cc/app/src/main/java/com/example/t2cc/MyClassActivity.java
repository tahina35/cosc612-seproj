package com.example.t2cc;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.t2cc.FirestoreConnections.ClassRosterCollectionAccessors;
import com.example.t2cc.FirestoreConnections.ClassesCollectionAccessors;
import com.example.t2cc.FirestoreConnections.TeacherCollectionAccessors;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.android.gms.tasks.Tasks.whenAllSuccess;

public class MyClassActivity extends BaseActivity {

  final static String TAG = "T2CC:MyClasses";
  MyClassAdapter mAdapter;
  RecyclerView mRecyclerView;
  ProgressBar mProgressBar;
  TextView emptyClass;

  private List<ClassListInformation> myClassesInfo;
  private Map<String, ClassListInformation> myClassesInfoHash;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_myclass);

    // get recycle view
    mRecyclerView = findViewById(R.id.myClassRecycleView);
    emptyClass = findViewById(R.id.myClassEmpty);
    mProgressBar = findViewById(R.id.myClassProgressBar);
    mRecyclerView.setVisibility(View.GONE);
    emptyClass.setVisibility(View.GONE);


    myClassesInfoHash = new HashMap<>();
    myClassesInfo = new ArrayList<>();

    findViewById(R.id.myClassBodyLabel).setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            populateMyClassesViewData();
          }
        });

    populateMyClassesViewData().addOnCompleteListener(
        new OnCompleteListener<List<Object>>() {
          @Override
          public void onComplete(@NonNull Task<List<Object>> task) {
            if (task.isSuccessful()) {
              Log.d(TAG, "setUpAdapterOnCreate:success");
              setupMyClassesAdapter(myClassesInfo);
              mProgressBar.setVisibility(View.GONE);
              mRecyclerView.setVisibility(View.VISIBLE);
              if (mAdapter.getItemCount() == 0) {
                emptyClass.setVisibility(View.VISIBLE);
              }
            } else {
              Log.w(TAG, "setUpAdapterOnCreate:failure");
              mProgressBar.setVisibility(View.GONE);
              emptyClass.setVisibility(View.VISIBLE);
            }
          }
        });

  }

  private void setupMyClassesAdapter(List<ClassListInformation> myClassesInfo) {
    mAdapter = new MyClassAdapter(myClassesInfo, getApplication());
    mRecyclerView.setAdapter(mAdapter);
    mRecyclerView.setLayoutManager(new LinearLayoutManager(MyClassActivity.this));
  }

  private Task<List<Object>> populateMyClassesViewData() {
    Task<List<Object>> gatherSubscribedClassListInfo = whenAllSuccess(
        ClassRosterCollectionAccessors.getUsersSubscribedClassesTask(mCurrentUserID)
            .continueWithTask(
                new Continuation<QuerySnapshot, Task<QuerySnapshot>>() {
                  @Override
                  public Task<QuerySnapshot> then(
                      @NonNull Task<QuerySnapshot> task) throws Exception {
                    QuerySnapshot myClassesResult = task.getResult();
                    List<String> classIDs = new ArrayList<>();
                    for (DocumentSnapshot cDoc : myClassesResult.getDocuments()) {
                      classIDs.add(cDoc.getId());
                    }
                    return ClassesCollectionAccessors.getSpecificClassesInfoTask(classIDs);
                  }
                }));
    // leaving this for when we add message count
    gatherSubscribedClassListInfo.addOnCompleteListener(new OnCompleteListener<List<Object>>() {
      @Override
      public void onComplete(@NonNull final Task<List<Object>> task) {
        if (task.isSuccessful()) {
          Log.d(TAG, "populateMyClassesViewData:success");
          List<Object> docs = task.getResult();
          final QuerySnapshot myClassesResult = (QuerySnapshot) docs.get(0);

          final List<String> tl = new ArrayList<>();
          for (DocumentSnapshot doc : myClassesResult.getDocuments()) {
            tl.add((String) doc.get("teacher_id"));
          }
          final Map<String, Map<String, Object>> allTeachersInfoHash = new HashMap<>();
          TeacherCollectionAccessors.mTeachersRef
              .get()
              .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> teacherInfoTask) {
                  if (teacherInfoTask.isSuccessful()) {
                    QuerySnapshot allTeachersResult = teacherInfoTask.getResult();
                    for (DocumentSnapshot teacherInfo : allTeachersResult) {
                      String teacherID = teacherInfo.getId();
                      if (tl.contains(teacherID)) {
                        allTeachersInfoHash.put(teacherID, teacherInfo.getData());
                      }
                    }


                    for (QueryDocumentSnapshot classDocument : myClassesResult) {
                      Map<String, Object> classInfo = classDocument.getData();
                      String classID = classDocument.getId();

                      String className =
                          (String) classInfo
                              .get(ClassesCollectionAccessors.mClassesCollectionFieldTitle);
                      String classNum = String.format("%1$10s%2$10s",
                          classInfo
                              .get(ClassesCollectionAccessors.mClassesCollectionFieldCourseNumber),
                          classInfo.get(ClassesCollectionAccessors.mClassesCollectionFieldSection));
                      String teacherID =
                          (String) classInfo
                              .get(ClassesCollectionAccessors.mClassesCollectionFieldTeacherID);
                      Map<String, Object> teacherInfo = allTeachersInfoHash.get(teacherID);
                      String teacherName = String.format("%s %s",
                          StringUtils.capitalize( (String) teacherInfo
                              .get(TeacherCollectionAccessors.mTeacherCollectionFieldFirstName)),
                          StringUtils.capitalize((String) teacherInfo
                              .get(TeacherCollectionAccessors.mTeacherCollectionFieldLastName)));

                      ClassListInformation cli = new ClassListInformation(MyClassActivity.this,
                          classID, className, classNum, teacherName);
                      myClassesInfoHash.put(classID, cli);
                    }
                    myClassesInfo.clear();
                    myClassesInfo.addAll(myClassesInfoHash.values());
                    if (mAdapter != null) {
                      mAdapter.notifyDataSetChanged();
                      if (mAdapter.getItemCount() > 0) {
                        emptyClass.setVisibility(View.GONE);
                      }
                    }
                  } else {
                    Log.w(TAG, "Get Teacher info failed", teacherInfoTask.getException());
                  }
                }
              });
        } else {
          Log.w(TAG, "populateMyClassesViewData:failure:", task.getException());
        }
      }
    });
    return gatherSubscribedClassListInfo;
  }

  void handleUnsubscribeToggle(MyClassActivity mcaObject, String classID) {
    Log.d(mcaObject.TAG, "handleSubscriptionToggle");
    mcaObject.unsubscribeFromClass(classID);
  }

  private void unsubscribeFromClass(final String classID) {
    Log.d(TAG, "unsubscribeFromClass");
    final DocumentReference classRosterRef = ClassRosterCollectionAccessors.mClassRosterRef
        .document(classID);
    mFBDB.runTransaction(new Transaction.Function<Void>() {
      @Nullable
      @Override
      public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
        DocumentSnapshot rosterClassDoc = transaction.get(classRosterRef);
        if (rosterClassDoc.exists()) {
          transaction.update(classRosterRef,
              ClassRosterCollectionAccessors.mClassRosterCollectionFieldStudents,
              FieldValue.arrayRemove(mCurrentUserID));
        } else {
          Log.w(TAG, "Attempted to unsubscribe from class with no roster.");
        }
        return null;
      }
    }).addOnCompleteListener(new OnCompleteListener<Void>() {
      @Override
      public void onComplete(@NonNull Task<Void> task) {
        if (task.isSuccessful()) {
          Log.d(TAG, "unsubscribeFromClass:success: " + classID);
          myClassesInfoHash.remove(classID);
          myClassesInfo.clear();
          myClassesInfo.addAll(myClassesInfoHash.values());
          mAdapter.notifyDataSetChanged();
        } else {
          Log.w(TAG, "unsubscribeFromClass:failure: " + classID, task.getException());
        }
      }
    });
  }
}
